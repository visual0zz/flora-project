package com.flora.sanctum.store.impl;

import com.flora.sanctum.store.Base58;
import com.flora.sanctum.store.Block;
import com.flora.sanctum.store.BlockHeader;
import com.flora.sanctum.store.Codec;
import com.flora.sanctum.store.ObjectStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Markdown 块集合存储实现（见设计 04b）。
 * <p>
 * - 库根文件夹下的 markdown 文件；独立文件默认整文件一个块。
 * - 扫描：读文件内容，剥离首尾空白/换行（独立文件整读），或切"连续 base58 串"（共享文件）。
 * - 写回：重定位目标块原位替换 base58 串。
 * - 删除：独立文件物理删，共享文件软删除（首字符后插 !）。
 */
public final class MarkdownObjectStore implements ObjectStore {

    private final Path root;

    public MarkdownObjectStore(Path root) {
        this.root = root;
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("cannot create root: " + root, e);
        }
    }

    public Path root() {
        return root;
    }

    @Override
    public byte[] get(UUID blockUuid, Codec codec) {
        Block block = find(blockUuid);
        if (block == null) {
            return null;
        }
        byte[] data = block.deobfuscated();
        if (codec == null) {
            return data;
        }
        return codec.decode(block.obfuscated());
    }

    @Override
    public void put(UUID blockUuid, byte[] data, Codec codec) {
        byte[] toWrite = codec == null ? data : codec.encode(data);
        // 已有则原位替换，否则新建独立文件
        Block existing = find(blockUuid);
        if (existing != null) {
            replace(existing, toWrite);
        } else {
            Path file = root.resolve(blockUuid + ".md");
            try {
                Files.writeString(file, Base58.encode(toWrite) + "\n", StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("write failed: " + file, e);
            }
        }
    }

    @Override
    public void delete(UUID blockUuid) {
        Block block = find(blockUuid);
        if (block == null) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(block.file(), StandardCharsets.UTF_8);
            // 独立文件（恰一行且只含该块）→ 物理删
            if (lines.size() == 1 && lines.get(0).trim().equals(block.base58())) {
                Files.deleteIfExists(block.file());
                return;
            }
            // 共享文件 → 软删除：首字符后插入 !（只改该串，保留行内其它正文）
            if (block.base58().length() < 1) {
                return;
            }
            String soft = block.base58().substring(0, 1) + "!" + block.base58().substring(1);
            int idx = (int) block.line() - 1;
            if (idx >= 0 && idx < lines.size()) {
                String line = lines.get(idx);
                lines.set(idx, line.replace(block.base58(), soft));
                Files.write(block.file(), lines, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new IllegalStateException("delete failed", e);
        }
    }

    @Override
    public List<UUID> list() {
        List<UUID> out = new ArrayList<>();
        for (Block b : scan()) {
            out.add(b.uuid());
        }
        return out;
    }

    @Override
    public List<Block> scan() {
        List<Block> blocks = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return blocks;
        }
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .forEach(p -> scanFile(p, blocks));
        } catch (IOException e) {
            throw new IllegalStateException("scan failed", e);
        }
        return blocks;
    }

    private void scanFile(Path file, List<Block> out) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                // 提取连续 base58 串
                for (int start = 0; start < line.length(); ) {
                    int s = start;
                    while (s < line.length() && !isBase58Char(line.charAt(s))) {
                        s++;
                    }
                    if (s >= line.length()) {
                        break;
                    }
                    int e = s;
                    while (e < line.length() && isBase58Char(line.charAt(e))) {
                        e++;
                    }
                    String candidate = line.substring(s, e);
                    // 软删除块（含 ! 断开）不识别
                    addIfBlock(file, i + 1, candidate, out);
                    start = e;
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("read failed: " + file, e);
        }
    }

    private void addIfBlock(Path file, long line, String candidate, List<Block> out) {
        if (candidate.length() < 12) {
            return;
        }
        byte[] bytes;
        try {
            bytes = Base58.decode(candidate);
        } catch (IllegalArgumentException e) {
            return;
        }
        byte[] deobfuscated;
        try {
            deobfuscated = BlockHeader.deobfuscate(bytes);
        } catch (IllegalArgumentException e) {
            return;
        }
        if (BlockHeader.isBlock(deobfuscated)) {
            out.add(new Block(file, line, candidate, bytes, deobfuscated));
        }
    }

    private Block find(UUID uuid) {
        for (Block b : scan()) {
            if (b.uuid().equals(uuid)) {
                return b;
            }
        }
        return null;
    }

    private void replace(Block block, byte[] newBytes) {
        String newBase58 = Base58.encode(newBytes);
        try {
            List<String> lines = Files.readAllLines(block.file(), StandardCharsets.UTF_8);
            int idx = (int) block.line() - 1;
            if (idx >= 0 && idx < lines.size()) {
                lines.set(idx, lines.get(idx).replace(block.base58(), newBase58));
                Files.write(block.file(), lines, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new IllegalStateException("replace failed", e);
        }
    }

    private static boolean isBase58Char(char c) {
        return Base58.isBase58(String.valueOf(c));
    }
}
