package com.flora.sanctum.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TotpTest {

    // RFC 6238 测试向量（SHA1, secret=12345678901234567890, 8 位码）
    @Test
    void rfc6238TestVector() {
        byte[] secret = "12345678901234567890".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals("94287082", Totp.generate(secret, 59, 8, 30));
        assertEquals("07081804", Totp.generate(secret, 1111111109L, 8, 30));
        assertEquals("14050471", Totp.generate(secret, 1111111111L, 8, 30));
    }

    @Test
    void sixDigitDefault() {
        byte[] secret = "12345678901234567890".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        // 与已知 6 位向量一致（T=59 应为 287082）
        assertEquals("287082", Totp.generate(secret, 59, 6, 30));
    }

    @Test
    void base32DecodeRoundTrip() {
        // flora-root 的基础能力 Base32：base32("FOO") = "IZHU6==="；解码（去填充）应还原 "FOO"
        byte[] decoded = com.flora.root.codec.Base32.decode("IZHU6");
        assertEquals("FOO", new String(decoded, java.nio.charset.StandardCharsets.UTF_8));
    }
}
