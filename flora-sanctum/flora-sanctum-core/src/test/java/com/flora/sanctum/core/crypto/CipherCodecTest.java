package com.flora.sanctum.core.crypto;

import com.flora.sanctum.core.crypto.impl.CipherCodec;
import com.flora.sanctum.core.crypto.impl.SecureRandomSource;
import com.flora.sanctum.core.crypto.impl.Envelope;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CipherCodecTest {

    @Test
    void encodeDecodeRoundTrip() {
        byte[] dek = new byte[32];
        new SecureRandomSource().nextBytes(dek);
        CipherCodec codec = new CipherCodec(dek, dek);

        UUID uuid = UUID.randomUUID();
        byte[] plaintext = "微博 password 密码".getBytes(StandardCharsets.UTF_8);

        byte[] block = codec.encode(uuid, plaintext, "42");
        // 落盘块为信封原始字节，以固定 magic 开头（无异或混淆）
        assertTrue(block[0] == Envelope.MAGIC[0] && block[1] == Envelope.MAGIC[1]);

        CipherCodec.DecodedBlock decoded = codec.decode(block, "42");
        assertEquals(uuid, decoded.uuid);
        assertArrayEquals(plaintext, decoded.plaintext);
    }

    @Test
    void decodeFailsOnWrongTimestamp() {
        byte[] dek = new byte[32];
        new SecureRandomSource().nextBytes(dek);
        CipherCodec codec = new CipherCodec(dek, dek);
        byte[] obfuscated = codec.encode(UUID.randomUUID(), "data".getBytes(StandardCharsets.UTF_8), "7");
        assertThrows(IllegalStateException.class, () -> codec.decode(obfuscated, "8"));
    }

    @Test
    void decodeFailsOnTamperedBlock() {
        byte[] dek = new byte[32];
        new SecureRandomSource().nextBytes(dek);
        CipherCodec codec = new CipherCodec(dek, dek);

        byte[] obfuscated = codec.encode(UUID.randomUUID(), "data".getBytes(StandardCharsets.UTF_8), "0");
        // 篡改一个字节
        obfuscated[10] ^= 0x01;
        assertThrows(IllegalStateException.class, () -> codec.decode(obfuscated, "0"));
    }
}
