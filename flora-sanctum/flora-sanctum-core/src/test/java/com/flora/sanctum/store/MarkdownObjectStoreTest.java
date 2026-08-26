package com.flora.sanctum.store;

import com.flora.sanctum.crypto.impl.CipherCodec;
import com.flora.sanctum.crypto.impl.SecureRandomSource;
import com.flora.sanctum.store.impl.CipherCodecAdapter;
import com.flora.sanctum.store.impl.MarkdownObjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownObjectStoreTest {

    @TempDir
    Path dir;

    private MarkdownObjectStore newStore() {
        return new MarkdownObjectStore(dir);
    }

    private CipherCodec newCodec() {
        byte[] dek = new byte[32];
        new SecureRandomSource().nextBytes(dek);
        return new CipherCodec(dek, dek);
    }

    @Test
    void putGetRoundTrip() {
        MarkdownObjectStore store = newStore();
        CipherCodec codec = newCodec();
        UUID uuid = UUID.randomUUID();
        byte[] plain = "hello 密码".getBytes(StandardCharsets.UTF_8);

        store.put(uuid, plain, new CipherCodecAdapter(codec, uuid), "1");
        byte[] got = store.get(uuid, new CipherCodecAdapter(codec, uuid));
        assertArrayEquals(plain, got);
    }

    @Test
    void putCreatesGitStylePath() throws Exception {
        MarkdownObjectStore store = newStore();
        CipherCodec codec = newCodec();
        UUID uuid = UUID.randomUUID();
        store.put(uuid, "data".getBytes(), new CipherCodecAdapter(codec, uuid), "1");
        // 路径：{前2字符}/{后30字符}.md（无连字符）
        String hex = uuid.toString().replace("-", "");
        Path file = dir.resolve(hex.substring(0, 2)).resolve(hex.substring(2) + ".md");
        assertTrue(java.nio.file.Files.exists(file), "expected file at " + file);
        assertFalse(java.nio.file.Files.exists(dir.resolve(uuid + ".md")));
        List<Block> blocks = store.scan();
        assertEquals(1, blocks.size());
        assertEquals(uuid, blocks.get(0).uuid());
        // 文件内容恰为一行 timestamp:base58
        String content = java.nio.file.Files.readString(file).trim();
        assertEquals(1, content.lines().count());
        assertTrue(content.matches("\\d+:[1-9A-HJ-NP-Za-km-z]+"));
    }

    @Test
    void updateOverwritesSingleFile() {
        MarkdownObjectStore store = newStore();
        CipherCodec codec = newCodec();
        UUID uuid = UUID.randomUUID();
        store.put(uuid, "v1".getBytes(), new CipherCodecAdapter(codec, uuid), "1");
        store.put(uuid, "v2-updated".getBytes(), new CipherCodecAdapter(codec, uuid), "2");
        byte[] got = store.get(uuid, new CipherCodecAdapter(codec, uuid));
        assertArrayEquals("v2-updated".getBytes(), got);
        assertEquals(1, store.scan().size());
    }

    @Test
    void deleteRemovesFile() {
        MarkdownObjectStore store = newStore();
        CipherCodec codec = newCodec();
        UUID uuid = UUID.randomUUID();
        store.put(uuid, "data".getBytes(), new CipherCodecAdapter(codec, uuid), "1");
        store.delete(uuid);
        String hex = uuid.toString().replace("-", "");
        Path file = dir.resolve(hex.substring(0, 2)).resolve(hex.substring(2) + ".md");
        assertFalse(java.nio.file.Files.exists(file));
        assertTrue(store.scan().isEmpty());
    }

    @Test
    void manyBlocksSpreadAcrossSubdirs() {
        MarkdownObjectStore store = newStore();
        CipherCodec codec = newCodec();
        int n = 64;
        for (int i = 0; i < n; i++) {
            UUID uuid = UUID.randomUUID();
            store.put(uuid, ("data-" + i).getBytes(), new CipherCodecAdapter(codec, uuid), String.valueOf(i));
        }
        assertEquals(n, store.scan().size());
        assertEquals(n, store.list().size());
        // 每个文件一层子目录（root 下直接子项都是目录，非 md）
        try (var stream = java.nio.file.Files.list(dir)) {
            assertTrue(stream.allMatch(p -> java.nio.file.Files.isDirectory(p)
                    || !p.getFileName().toString().endsWith(".md")));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** uuid 前 2 字符（分片目录）在 256 个桶上分布均匀：v4 UUID 第 1 字节是 8 位随机。 */
    @Test
    void uuidPrefixUniform() {
        Map<String, Integer> counts = new HashMap<>();
        int n = 4096;
        for (int i = 0; i < n; i++) {
            String hex = UUID.randomUUID().toString().replace("-", "");
            counts.merge(hex.substring(0, 2), 1, Integer::sum);
        }
        // 4096 样本 × 1/256 ≈ 16/桶；均匀性：极差 < 4 倍均值（无冷热桶）
        int mean = n / 256;
        for (int c : counts.values()) {
            assertTrue(c > mean / 4 && c < mean * 4, "prefix count out of range: " + c);
        }
    }

    @Test
    void putReturnsBlockWithMetadata() {
        MarkdownObjectStore store = newStore();
        CipherCodec codec = newCodec();
        UUID uuid = UUID.randomUUID();
        Block written = store.put(uuid, "data".getBytes(), new CipherCodecAdapter(codec, uuid), "7");
        assertNotNull(written);
        assertEquals(uuid, written.uuid());
        assertEquals("7", written.timestampText());
        String hex = uuid.toString().replace("-", "");
        Path file = dir.resolve(hex.substring(0, 2)).resolve(hex.substring(2) + ".md");
        assertEquals(file, written.file());
    }

    @Test
    void putLeavesNoTmpFile() throws Exception {
        MarkdownObjectStore store = newStore();
        CipherCodec codec = newCodec();
        UUID uuid = UUID.randomUUID();
        store.put(uuid, "data".getBytes(), new CipherCodecAdapter(codec, uuid), "1");
        try (var stream = java.nio.file.Files.walk(dir)) {
            assertTrue(stream.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")),
                    "临时文件残留");
        }
    }

    @Test
    void putReplacesAtomicallyWithCompleteFile() throws Exception {
        MarkdownObjectStore store = newStore();
        CipherCodec codec = newCodec();
        UUID uuid = UUID.randomUUID();
        store.put(uuid, "v1".getBytes(), new CipherCodecAdapter(codec, uuid), "1");
        store.put(uuid, "v2-updated".getBytes(), new CipherCodecAdapter(codec, uuid), "2");
        // 覆盖写后目标文件存在且为完整单行（原子替换，不留半写/损坏块）。
        String hex = uuid.toString().replace("-", "");
        Path file = dir.resolve(hex.substring(0, 2)).resolve(hex.substring(2) + ".md");
        String content = java.nio.file.Files.readString(file).trim();
        assertEquals(1, content.lines().count());
        assertTrue(content.matches("\\d+:[1-9A-HJ-NP-Za-km-z]+"));
        assertArrayEquals("v2-updated".getBytes(), store.get(uuid, new CipherCodecAdapter(codec, uuid)));
        try (var stream = java.nio.file.Files.walk(dir)) {
            assertTrue(stream.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")),
                    "覆盖写后临时文件残留");
        }
    }
}
