package com.flora.sanctum.core.store.impl;

import com.flora.root.codec.Base58;
import com.flora.sanctum.core.store.Block;
import com.flora.sanctum.core.store.BlockHeader;
import com.flora.sanctum.core.store.Codec;
import com.flora.sanctum.core.store.ObjectStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Markdown 块存储实现：git 风格两层目录分片 + 一文件一块（见设计 04b）。
 * <p>
 * 布局：{@code root/{a}/{b}/{rest}.md}，其中 {@code a}、{@code b} 分别是 uuid 无连字符 hex
 * 的第 1、第 2 个字符（两层单字母目录分片），{@code rest} 是剩余 30 字符。
 * 每个文件恰好一个块，内容为单行 {@code timestamp:base58}。
 * 不再支持一个文件多个块 / 用户正文与密文交错（旧格式兼容已去除）。
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

    /** uuid → 32 位无连字符 hex（前 2 字符各作一层单字母目录分片）。 */
    static String hexOf(UUID uuid) {
        return uuid.toString().replace("-", "");
    }

    /** 块文件路径：{@code root/{第1字符}/{第2字符}/{后30字符}.md}。 */
    Path fileOf(UUID uuid) {
        String hex = hexOf(uuid);
        return root.resolve(hex.substring(0, 1)).resolve(hex.substring(1, 2)).resolve(hex.substring(2) + ".md");
    }

    @Override
    public byte[] get(UUID blockUuid, Codec codec) {
        Path file = fileOf(blockUuid);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8).trim();
            int colon = content.indexOf(':');
            if (colon <= 0 || colon >= content.length() - 1) {
                return null;
            }
            String timestamp = content.substring(0, colon);
            byte[] block = Base58.decode(content.substring(colon + 1));
            if (codec == null) {
                return block;
            }
            return codec.decode(block, timestamp);
        } catch (Exception e) {
            throw new IllegalStateException("read failed: " + file, e);
        }
    }

    @Override
    public Block put(UUID blockUuid, byte[] data, Codec codec, String timestamp) {
        byte[] toWrite = codec == null ? data : codec.encode(data, timestamp);
        Path file = fileOf(blockUuid);
        // 同目录临时文件 → 原子替换目标，避免崩溃/掉电留下半写块（半写块不可解密即损坏）。
        Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(tmp, timestamp + ":" + Base58.encode(toWrite) + "\n", StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return new Block(file, 1, timestamp, toWrite, toWrite);
        } catch (IOException e) {
            throw new IllegalStateException("write failed: " + file, e);
        } finally {
            // 任何异常路径下都清理可能残留的临时文件（scan 仅匹配 .md，残留 .tmp 不会被误读）。
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignore) {
            }
        }
    }

    @Override
    public void delete(UUID blockUuid) {
        try {
            Files.deleteIfExists(fileOf(blockUuid));
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

    /** 解析单行 {@code timestamp:base58} 文件为一个块；损坏/不可识别则跳过。 */
    private void scanFile(Path file, List<Block> out) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8).trim();
            int colon = content.indexOf(':');
            if (colon <= 0 || colon >= content.length() - 1) {
                return;
            }
            String ts = content.substring(0, colon);
            String candidate = content.substring(colon + 1);
            if (candidate.length() < 12) {
                return;
            }
            byte[] bytes = Base58.decode(candidate);
            if (!BlockHeader.isBlock(bytes)) {
                return;
            }
            out.add(new Block(file, 1, ts, bytes, bytes));
        } catch (Exception ignore) {
            // 损坏/非本应用文件，跳过
        }
    }
}
