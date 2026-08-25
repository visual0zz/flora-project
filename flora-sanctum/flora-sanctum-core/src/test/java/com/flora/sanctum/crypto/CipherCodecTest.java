package com.flora.sanctum.crypto;

import com.flora.sanctum.crypto.impl.CipherCodec;
import com.flora.sanctum.crypto.impl.SecureRandomSource;
import com.flora.sanctum.crypto.impl.Envelope;

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

        byte[] obfuscated = codec.encode(uuid, plaintext, "42");
        // 落盘字节不应以固定 magic 开头（异或混淆）
        assertFalse(obfuscated[0] == Envelope.MAGIC[0] && obfuscated[1] == Envelope.MAGIC[1]);

        CipherCodec.DecodedBlock decoded = codec.decode(obfuscated, "42");
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
