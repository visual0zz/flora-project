package com.flora.sanctum.store.impl;

import com.flora.root.codec.Base58;
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
        return codec.decode(block.obfuscated(), block.timestamp());
    }

    @Override
    public void put(UUID blockUuid, byte[] data, Codec codec, long timestamp) {
        byte[] toWrite = codec == null ? data : codec.encode(data, timestamp);
        // 已有则原位替换，否则新建独立文件
        Block existing = find(blockUuid);
        if (existing != null) {
            replace(existing, toWrite, timestamp);
        } else {
            Path file = root.resolve(blockUuid + ".md");
            try {
                Files.writeString(file, timestamp + ":" + Base58.encode(toWrite) + "\n", StandardCharsets.UTF_8);
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
            String token = block.timestamp() + ":" + block.base58();
            // 独立文件（恰一行且只含该块）→ 物理删
            if (lines.size() == 1 && lines.get(0).trim().equals(token)) {
                Files.deleteIfExists(block.file());
                return;
            }
            // 共享文件 → 软删除：base58 首字符后插入 !（只改该串，保留行内其它正文）
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
                // 提取 "数字:base58" 块（时间戳前缀 + 冒号 + base58 密文）
                for (int start = 0; start < line.length(); ) {
                    int tsStart = start;
                    while (tsStart < line.length() && Character.isDigit(line.charAt(tsStart))) {
                        tsStart++;
                    }
                    if (tsStart == start || tsStart >= line.length() || line.charAt(tsStart) != ':') {
                        start = nextCandidateStart(line, tsStart);
                        continue;
                    }
                    int s = tsStart + 1;
                    while (s < line.length() && isBase58Char(line.charAt(s))) {
                        s++;
                    }
                    if (s == tsStart + 1) {
                        start = nextCandidateStart(line, s);
                        continue;
                    }
                    String ts = line.substring(start, tsStart);
                    String candidate = line.substring(tsStart + 1, s);
                    addIfBlock(file, i + 1, ts, candidate, out);
                    start = s;
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("read failed: " + file, e);
        }
    }

    /** 跳到下一个可能候选起点：跳过非数字字符，直到下一段连续数字开头。 */
    private static int nextCandidateStart(String line, int from) {
        while (from < line.length() && !Character.isDigit(line.charAt(from))) {
            from++;
        }
        return from;
    }

    private void addIfBlock(Path file, long line, String timestamp, String candidate, List<Block> out) {
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
            out.add(new Block(file, line, Long.parseLong(timestamp), candidate, bytes, deobfuscated));
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

    private void replace(Block block, byte[] newBytes, long timestamp) {
        String newBase58 = Base58.encode(newBytes);
        String oldToken = block.timestamp() + ":" + block.base58();
        String newToken = timestamp + ":" + newBase58;
        try {
            List<String> lines = Files.readAllLines(block.file(), StandardCharsets.UTF_8);
            int idx = (int) block.line() - 1;
            if (idx >= 0 && idx < lines.size()) {
                lines.set(idx, lines.get(idx).replace(oldToken, newToken));
                Files.write(block.file(), lines, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new IllegalStateException("replace failed", e);
        }
    }

    private static boolean isBase58Char(char c) {
        return Base58.isValidBase58(String.valueOf(c));
    }
}
