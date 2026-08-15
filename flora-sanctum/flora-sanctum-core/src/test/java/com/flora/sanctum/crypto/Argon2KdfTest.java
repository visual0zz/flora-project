package com.flora.sanctum.crypto;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class Argon2KdfTest {

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
}
