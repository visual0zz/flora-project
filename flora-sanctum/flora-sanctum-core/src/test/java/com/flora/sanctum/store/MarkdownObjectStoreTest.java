package com.flora.sanctum.store;

import com.flora.sanctum.crypto.CipherCodec;
import com.flora.sanctum.crypto.SecureRandomSource;
import com.flora.sanctum.store.impl.CipherCodecAdapter;
import com.flora.sanctum.store.impl.MarkdownObjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
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

        store.put(uuid, plain, new CipherCodecAdapter(codec, uuid));
        byte[] got = store.get(uuid, new CipherCodecAdapter(codec, uuid));
        assertArrayEquals(plain, got);
    }

    @Test
    void putCreatesIndependentFile() {
        MarkdownObjectStore store = newStore();
        CipherCodec codec = newCodec();
        UUID uuid = UUID.randomUUID();
        store.put(uuid, "data".getBytes(), new CipherCodecAdapter(codec, uuid));
        assertTrue(java.nio.file.Files.exists(dir.resolve(uuid + ".md")));
        List<Block> blocks = store.scan();
        assertEquals(1, blocks.size());
        assertEquals(uuid, blocks.get(0).uuid());
    }

    @Test
    void updateReplacesInPlace() {
        MarkdownObjectStore store = newStore();
        CipherCodec codec = newCodec();
        UUID uuid = UUID.randomUUID();
        store.put(uuid, "v1".getBytes(), new CipherCodecAdapter(codec, uuid));
        store.put(uuid, "v2-updated".getBytes(), new CipherCodecAdapter(codec, uuid));
        byte[] got = store.get(uuid, new CipherCodecAdapter(codec, uuid));
        assertArrayEquals("v2-updated".getBytes(), got);
        // 仍是一个文件一个块
        assertEquals(1, store.scan().size());
    }

    @Test
    void deleteIndependentFile() {
        MarkdownObjectStore store = newStore();
        CipherCodec codec = newCodec();
        UUID uuid = UUID.randomUUID();
        store.put(uuid, "data".getBytes(), new CipherCodecAdapter(codec, uuid));
        store.delete(uuid);
        assertFalse(java.nio.file.Files.exists(dir.resolve(uuid + ".md")));
        assertTrue(store.scan().isEmpty());
    }

    @Test
    void sharedFileSoftDelete() throws Exception {
        MarkdownObjectStore store = newStore();
        CipherCodec codec = newCodec();
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();
        store.put(u1, "block-one-data".getBytes(), new CipherCodecAdapter(codec, u1));
        store.put(u2, "block-two-data".getBytes(), new CipherCodecAdapter(codec, u2));
        // 取两个块的 base58，合并进一个共享文件
        String b1 = store.scan().stream().filter(b -> b.uuid().equals(u1)).findFirst().get().base58();
        String b2 = store.scan().stream().filter(b -> b.uuid().equals(u2)).findFirst().get().base58();
        java.nio.file.Files.delete(dir.resolve(u1 + ".md"));
        java.nio.file.Files.delete(dir.resolve(u2 + ".md"));
        java.nio.file.Files.writeString(dir.resolve("shared.md"), b1 + "\n" + b2 + "\n");

        // 删除 u1 → 软删除（首字符后插 !）
        store.delete(u1);
        String shared = java.nio.file.Files.readString(dir.resolve("shared.md"));
        assertTrue(shared.contains("!"), "should be soft-deleted with !");
        // u2 仍可读
        assertTrue(store.scan().stream().anyMatch(b -> b.uuid().equals(u2)));
    }
}
