package com.flora.sanctum.crypto;

import com.flora.sanctum.crypto.impl.Argon2;
import com.flora.sanctum.crypto.impl.SecureRandomSource;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Argon2 与 Argon2Kdf 测试。
 * <p>KAT 值由替换前的 Bouncy Castle 参考实现生成，用于回归验证自研实现与其逐字节一致。</p>
 */
class Argon2KdfTest {

    private static final byte[] PWD = "password123".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SALT = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};

    @Test
    void digestMatchesBcReference() {
        assertEquals("e7a2e6593b28c0d663e64290e0194b02841265fd476fe14347af9efabac63db6",
                hex(Argon2.digest(PWD, SALT, 1024, 3, 4, 32)));
        assertEquals("cb5fa76a6cf590caf81182d1e404379944e99bfb6970969bdd9ec60afeb00fab",
                hex(Argon2.digest(PWD, SALT, 1024, 2, 1, 32)));
        assertEquals("31a059aa591eef1e0b5970134a1739139f8c4671326f4cf5fdefbbeda3fb5512",
                hex(Argon2.digest(PWD, SALT, 4096, 1, 2, 32)));
        assertEquals("94ee02c714e11ee78e9a7a00b1c8e1e9",
                hex(Argon2.digest(PWD, SALT, 64, 3, 4, 16)));
    }

    @Test
    void deriveProduces32Bytes() {
        byte[] salt = new byte[16];
        new SecureRandomSource().nextBytes(salt);
        Argon2Kdf kdf = new Argon2Kdf(salt);
        byte[] kek = kdf.derive("password123".toCharArray());
        assertEquals(32, kek.length);
    }

    @Test
    void samePasswordAndSaltSameKek() {
        byte[] salt = new byte[16];
        new SecureRandomSource().nextBytes(salt);
        Argon2Kdf kdf = new Argon2Kdf(salt);
        assertArrayEquals(kdf.derive("pw".toCharArray()), kdf.derive("pw".toCharArray()));
    }

    @Test
    void differentPasswordDifferentKek() {
        byte[] salt = new byte[16];
        new SecureRandomSource().nextBytes(salt);
        Argon2Kdf kdf = new Argon2Kdf(salt);
        assertFalse(Arrays.equals(kdf.derive("a".toCharArray()), kdf.derive("b".toCharArray())));
    }

    private static String hex(byte[] out) {
        return HexFormat.of().formatHex(out);
    }
}
