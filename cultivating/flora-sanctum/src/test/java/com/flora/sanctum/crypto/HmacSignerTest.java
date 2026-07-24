package com.flora.sanctum.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HmacSignerTest {

    @Test
    void signAndVerify() {
        byte[] key = new byte[32];
        SecureRandomHolder.get().nextBytes(key);
        byte[] data = "meta.json content".getBytes();

        byte[] signature = HmacSigner.sign(data, key);
        assertEquals(32, signature.length);

        assertTrue(HmacSigner.verify(data, key, signature));
    }

    @Test
    void verifyWithWrongKeyFails() {
        byte[] key1 = new byte[32];
        SecureRandomHolder.get().nextBytes(key1);
        byte[] key2 = new byte[32];
        SecureRandomHolder.get().nextBytes(key2);
        byte[] data = "meta.json content".getBytes();

        byte[] signature = HmacSigner.sign(data, key1);
        assertFalse(HmacSigner.verify(data, key2, signature));
    }

    @Test
    void verifyWithTamperedDataFails() {
        byte[] key = new byte[32];
        SecureRandomHolder.get().nextBytes(key);
        byte[] data = "meta.json content".getBytes();

        byte[] signature = HmacSigner.sign(data, key);

        byte[] tampered = "modified content".getBytes();
        assertFalse(HmacSigner.verify(tampered, key, signature));
    }

    @Test
    void invalidKeyLength() {
        byte[] shortKey = new byte[16];
        byte[] data = "test".getBytes();
        assertThrows(IllegalArgumentException.class,
                () -> HmacSigner.sign(data, shortKey));
    }
}
