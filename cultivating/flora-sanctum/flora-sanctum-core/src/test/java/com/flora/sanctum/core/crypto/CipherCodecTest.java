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
        // uuid 不写入块内，解码时须由调用方提供（文件块取自路径）
        assertArrayEquals(plaintext, codec.decode(block, uuid, "42"));
    }

    @Test
    void blockOmitsUuidHeaderField() {
        byte[] dek = new byte[32];
        new SecureRandomSource().nextBytes(dek);
        CipherCodec codec = new CipherCodec(dek, dek);

        UUID uuid = new UUID(0x0102030405060708L, 0x090A0B0C0D0E0F10L);
        byte[] block = codec.encode(uuid, "data".getBytes(StandardCharsets.UTF_8), "1");
        // 头 = magic(6)+version(1)+flags(1)+nonce(12)+keyId(8)：uuid 不占头部字节
        assertEquals(Envelope.HEADER_LEN, Envelope.MAGIC_LEN + 1 + 1 + Envelope.NONCE_LEN + Envelope.KEYID_LEN);
        // 该 uuid 的字节不应出现在块内的任何位置（既不入头也不入负载）
        byte[] id = CipherCodec.uuidBytes(uuid);
        for (int i = 0; i + id.length <= block.length; i++) {
            boolean hit = true;
            for (int j = 0; j < id.length; j++) {
                if (block[i + j] != id[j]) {
                    hit = false;
                    break;
                }
            }
            assertFalse(hit, "uuid 不应出现在块内");
        }
    }

    @Test
    void decodeFailsOnWrongTimestamp() {
        byte[] dek = new byte[32];
        new SecureRandomSource().nextBytes(dek);
        CipherCodec codec = new CipherCodec(dek, dek);
        UUID uuid = UUID.randomUUID();
        byte[] obfuscated = codec.encode(uuid, "data".getBytes(StandardCharsets.UTF_8), "7");
        assertThrows(IllegalStateException.class, () -> codec.decode(obfuscated, uuid, "8"));
    }

    @Test
    void decodeFailsOnWrongUuid() {
        byte[] dek = new byte[32];
        new SecureRandomSource().nextBytes(dek);
        CipherCodec codec = new CipherCodec(dek, dek);
        UUID uuid = UUID.randomUUID();
        byte[] obfuscated = codec.encode(uuid, "data".getBytes(StandardCharsets.UTF_8), "0");
        // uuid 参与 AAD 且不在块内：换一个 uuid（等价于块被移到别的路径）即认证失败
        assertThrows(IllegalStateException.class, () -> codec.decode(obfuscated, UUID.randomUUID(), "0"));
    }

    @Test
    void decodeFailsOnTamperedBlock() {
        byte[] dek = new byte[32];
        new SecureRandomSource().nextBytes(dek);
        CipherCodec codec = new CipherCodec(dek, dek);

        UUID uuid = UUID.randomUUID();
        byte[] obfuscated = codec.encode(uuid, "data".getBytes(StandardCharsets.UTF_8), "0");
        // 篡改一个字节（偏移 10 落在 nonce 内：头为 magic(6)+version(1)+flags(1)+nonce(12)+keyId(8)）
        obfuscated[10] ^= 0x01;
        assertThrows(IllegalStateException.class, () -> codec.decode(obfuscated, uuid, "0"));
    }
}
