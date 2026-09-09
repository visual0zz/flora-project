package com.flora.sanctum.core.crypto;

import com.flora.sanctum.core.crypto.impl.CipherCodec;
import com.flora.sanctum.core.crypto.impl.SecureRandomSource;
import com.flora.sanctum.core.crypto.impl.KeyIdIndex;
import com.flora.sanctum.core.crypto.impl.BlockResolver;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class KeyIdIndexTest {

    private static final SecureRandomSource RNG = new SecureRandomSource();

    @Test
    void resolverFindsCorrectDekAmongMany() {
        byte[] dek1 = new byte[32];
        byte[] dek2 = new byte[32];
        byte[] dek3 = new byte[32];
        RNG.nextBytes(dek1);
        RNG.nextBytes(dek2);
        RNG.nextBytes(dek3);
        byte[] repoSeed = new byte[32];
        RNG.nextBytes(repoSeed);

        KeyIdIndex index = new KeyIdIndex();
        index.register(dek1);
        index.register(dek2);
        index.register(dek3);

        // 用 dek2 加密（产品路径：带 repoKeyIdSeed，keyId 可逆定位）
        byte[] encKey2 = derive(dek2);
        CipherCodec codec = new CipherCodec(encKey2, dek2, repoSeed, RNG);
        UUID uuid = UUID.randomUUID();
        byte[] plain = "secret data 密码".getBytes(StandardCharsets.UTF_8);
        byte[] block = codec.encode(uuid, plain, "0");

        // 通过 resolver 解析（候选含三个 DEK，应命中 dek2）
        BlockResolver resolver = new BlockResolver(index, () -> repoSeed);
        byte[] decoded = resolver.decode(block, uuid, "0");
        assertNotNull(decoded);
        assertArrayEquals(plain, decoded);
    }

    @Test
    void resolverReturnsNullForUnknownDek() {
        byte[] dekUsed = new byte[32];
        RNG.nextBytes(dekUsed);
        byte[] repoSeed = new byte[32];
        RNG.nextBytes(repoSeed);
        CipherCodec codec = new CipherCodec(derive(dekUsed), dekUsed, repoSeed, RNG);
        UUID uuid = UUID.randomUUID();
        byte[] block = codec.encode(uuid, "x".getBytes(), "0");

        // 索引里没有任何 DEK
        KeyIdIndex empty = new KeyIdIndex();
        BlockResolver resolver = new BlockResolver(empty, () -> repoSeed);
        assertNull(resolver.decode(block, uuid, "0"));
    }

    @Test
    void registerBuildsSingleEntryPerDek() {
        byte[] dek = new byte[32];
        RNG.nextBytes(dek);
        KeyIdIndex index = new KeyIdIndex();
        index.register(dek);
        assertEquals(1, index.size());
        // 按 dekId 查表命中
        byte[] dekId = KeyIdDeriver.dekId(dek);
        assertEquals(1, index.candidateCount(dekId));
    }

    private static byte[] derive(byte[] dek) {
        return com.flora.sanctum.core.crypto.impl.HkdfSha256.derive(dek, null, "sanctum-enc", 32);
    }
}
