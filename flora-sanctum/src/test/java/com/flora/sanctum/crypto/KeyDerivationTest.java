package com.flora.sanctum.crypto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class KeyDerivationTest {

    @Test
    void deriveWithValidParams() {
        char[] password = "correct horse battery staple".toCharArray();
        byte[] salt = new byte[16];
        SecureRandomHolder.get().nextBytes(salt);

        byte[] keyMaterial = KeyDerivation.derive(password, salt, KeyDerivation.MIN_ITERATIONS);
        assertEquals(64, keyMaterial.length);
    }

    @Test
    void deriveSamePasswordSameSaltSameKey() {
        char[] password = "test-password".toCharArray();
        byte[] salt = "0123456789abcdef".getBytes();

        byte[] km1 = KeyDerivation.derive(password, salt, 600_000);
        byte[] km2 = KeyDerivation.derive(password, salt, 600_000);

        assertArrayEquals(km1, km2);
    }

    @Test
    void deriveDifferentSaltDifferentKey() {
        char[] password = "test-password".toCharArray();
        byte[] salt1 = "0123456789abcdef".getBytes();
        byte[] salt2 = "fedcba9876543210".getBytes();

        byte[] km1 = KeyDerivation.derive(password, salt1, 600_000);
        byte[] km2 = KeyDerivation.derive(password, salt2, 600_000);

        assertFalse(java.util.Arrays.equals(km1, km2));
    }

    @Test
    void deriveEncryptionAndMacKeys() {
        char[] password = "test-password".toCharArray();
        byte[] salt = new byte[16];
        SecureRandomHolder.get().nextBytes(salt);

        byte[] km = KeyDerivation.derive(password, salt, KeyDerivation.MIN_ITERATIONS);
        byte[] encKey = KeyDerivation.encryptionKey(km);
        byte[] macKey = KeyDerivation.macKey(km);

        assertEquals(32, encKey.length);
        assertEquals(32, macKey.length);
        assertFalse(java.util.Arrays.equals(encKey, macKey));
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 1_000, 10_000, 100_000, 599_999})
    void rejectInvalidIterations(int iterations) {
        char[] password = "pwd".toCharArray();
        byte[] salt = new byte[16];
        assertThrows(IllegalArgumentException.class,
                () -> KeyDerivation.derive(password, salt, iterations));
    }

    @Test
    void rejectNullPassword() {
        byte[] salt = new byte[16];
        assertThrows(IllegalArgumentException.class,
                () -> KeyDerivation.derive(null, salt, KeyDerivation.MIN_ITERATIONS));
    }

    @Test
    void rejectEmptyPassword() {
        byte[] salt = new byte[16];
        assertThrows(IllegalArgumentException.class,
                () -> KeyDerivation.derive(new char[0], salt, KeyDerivation.MIN_ITERATIONS));
    }

    @Test
    void rejectShortSalt() {
        char[] password = "pwd".toCharArray();
        byte[] salt = new byte[15];
        assertThrows(IllegalArgumentException.class,
                () -> KeyDerivation.derive(password, salt, KeyDerivation.MIN_ITERATIONS));
    }
}
