package com.flora.sanctum.core.crypto;

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

    // RFC 6238 测试向量的 base32 形式（ASCII "12345678901234567890"）
    private static final String RFC_SECRET_B32 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    void uriMatchesBareSeed() {
        // otpauth:// URI 与裸种子应算出一致的验证码（RFC 向量 T=59，8 位）
        byte[] secret = "12345678901234567890".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals("94287082", Totp.generate(secret, 59, 8, 30));
        assertEquals("94287082",
                Totp.generateFromUri("otpauth://totp/acc?secret=" + RFC_SECRET_B32 + "&digits=8", 59));
    }

    @Test
    void uriDefaultDigitsIsSix() {
        // URI 未指定 digits 时默认 6 位；T=59 应为 287082
        assertEquals("287082",
                Totp.generateFromUri("otpauth://totp/acc?secret=" + RFC_SECRET_B32, 59));
    }

    @Test
    void uriCustomPeriodChangesCounter() {
        // period=60 改变计数器，结果应与裸种子的 period=60 一致
        byte[] secret = "12345678901234567890".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String expected = Totp.generate(secret, 59, 6, 60);
        assertEquals(expected,
                Totp.generateFromUri("otpauth://totp/acc?secret=" + RFC_SECRET_B32 + "&period=60", 59));
    }

    @Test
    void uriAlgorithmIsDeterministic() {
        // SHA256 / SHA512 应产出稳定（且彼此不同、与 SHA1 不同）的 6 位码
        String sha1 = Totp.generateFromUri("otpauth://totp/acc?secret=" + RFC_SECRET_B32 + "&algorithm=SHA1", 59);
        String sha256 = Totp.generateFromUri("otpauth://totp/acc?secret=" + RFC_SECRET_B32 + "&algorithm=SHA256", 59);
        String sha512 = Totp.generateFromUri("otpauth://totp/acc?secret=" + RFC_SECRET_B32 + "&algorithm=SHA512", 59);
        assertEquals(sha256, Totp.generateFromUri("otpauth://totp/acc?secret=" + RFC_SECRET_B32 + "&algorithm=SHA256", 59));
        assertEquals(6, sha256.length());
        assertEquals(6, sha512.length());
        assertNotEquals(sha1, sha256);
        assertNotEquals(sha1, sha512);
        assertNotEquals(sha256, sha512);
    }

    @Test
    void uriMissingSecretThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> Totp.generateFromUri("otpauth://totp/acc?issuer=x"));
    }

    @Test
    void uriHotpRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Totp.generateFromUri("otpauth://hotp/acc?secret=" + RFC_SECRET_B32 + "&counter=1"));
    }

    @Test
    void uriUnsupportedAlgorithmThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> Totp.generateFromUri("otpauth://totp/acc?secret=" + RFC_SECRET_B32 + "&algorithm=MD5"));
    }

    @Test
    void uriBadSchemeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> Totp.generateFromUri("https://example.com/totp"));
    }
}
