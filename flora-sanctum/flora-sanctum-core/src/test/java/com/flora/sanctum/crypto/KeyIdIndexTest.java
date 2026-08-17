package com.flora.sanctum.crypto;

import com.flora.sanctum.crypto.impl.CipherCodec;
import com.flora.sanctum.crypto.impl.SecureRandomSource;
import com.flora.sanctum.crypto.impl.KeyIdIndex;
import com.flora.sanctum.crypto.impl.Envelope;
import com.flora.sanctum.crypto.impl.BlockResolver;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class KeyIdIndexTest {

    @Test
    void resolverFindsCorrectDekAmongMany() {
        SecureRandomSource rng = new SecureRandomSource();
        byte[] dek1 = new byte[32];
        byte[] dek2 = new byte[32];
        byte[] dek3 = new byte[32];
        rng.nextBytes(dek1);
        rng.nextBytes(dek2);
        rng.nextBytes(dek3);

        KeyIdIndex index = new KeyIdIndex();
        index.register(dek1);
        index.register(dek2);
        index.register(dek3);

        // 用 dek2 加密
        byte[] encKey2 = derive(dek2);
        CipherCodec codec = new CipherCodec(encKey2, dek2, rng);
        UUID uuid = UUID.randomUUID();
        byte[] plain = "secret data 密码".getBytes(StandardCharsets.UTF_8);
        byte[] keyId = codec.makeKeyId();
        byte[] block = codec.encode(uuid, plain, keyId, 0);

        // 通过 resolver 解析（候选含三个 DEK，应命中 dek2）
        BlockResolver resolver = new BlockResolver(index);
        byte[] decoded = resolver.decode(block, 0);
        assertNotNull(decoded);
        assertArrayEquals(plain, decoded);
    }

    @Test
    void resolverReturnsNullForUnknownDek() {
        SecureRandomSource rng = new SecureRandomSource();
        byte[] dekUsed = new byte[32];
        rng.nextBytes(dekUsed);
        CipherCodec codec = new CipherCodec(derive(dekUsed), dekUsed, rng);
        byte[] block = codec.encode(UUID.randomUUID(), "x".getBytes(), codec.makeKeyId(), 0);

        // 索引里没有任何 DEK
        KeyIdIndex empty = new KeyIdIndex();
        BlockResolver resolver = new BlockResolver(empty);
        assertNull(resolver.decode(block, 0));
    }

    @Test
    void registerBuilds256KeyIds() {
        byte[] dek = new byte[32];
        new SecureRandomSource().nextBytes(dek);
        KeyIdIndex index = new KeyIdIndex();
        index.register(dek);
        assertEquals(256, index.size());
    }

    private static byte[] derive(byte[] dek) {
        return com.flora.sanctum.crypto.impl.HkdfSha256.derive(dek, null, "sanctum-enc", 32);
    }
}
