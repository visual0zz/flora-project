package com.flora.sanctum.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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
        byte[] keyId = codec.makeKeyId();

        byte[] obfuscated = codec.encode(uuid, plaintext, keyId);
        // 落盘字节不应以固定 magic 开头（异或混淆）
        assertFalse(obfuscated[0] == Envelope.MAGIC[0] && obfuscated[1] == Envelope.MAGIC[1]);

        CipherCodec.DecodedBlock decoded = codec.decode(obfuscated);
        assertEquals(uuid, decoded.uuid);
        assertArrayEquals(plaintext, decoded.plaintext);
    }

    @Test
    void decodeFailsOnTamperedBlock() {
        byte[] dek = new byte[32];
        new SecureRandomSource().nextBytes(dek);
        CipherCodec codec = new CipherCodec(dek, dek);

        byte[] obfuscated = codec.encode(UUID.randomUUID(), "data".getBytes(StandardCharsets.UTF_8), codec.makeKeyId());
        // 篡改一个字节
        obfuscated[10] ^= 0x01;
        assertThrows(IllegalStateException.class, () -> codec.decode(obfuscated));
    }

    @Test
    void makeKeyIdIsFourBytesAndReversibleByDek() {
        byte[] dek = new byte[32];
        new SecureRandomSource().nextBytes(dek);
        CipherCodec codec = new CipherCodec(dek, dek);
        byte[] keyId = codec.makeKeyId();
        assertEquals(4, keyId.length);
        // byte1 恢复：keyId[0]；byte2 = SHA256(DEK‖byte1)[0:3]
        byte byte1 = keyId[0];
        byte[] hash;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            md.update(dek);
            md.update(byte1);
            hash = md.digest();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        assertArrayEquals(Arrays.copyOfRange(hash, 0, 3), Arrays.copyOfRange(keyId, 1, 4));
    }
}
