package com.flora.sanctum.crypto;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AesGcmCipherTest {

    @Test
    void roundTripEncryptDecrypt() {
        byte[] key = new byte[32];
        SecureRandomHolder.get().nextBytes(key);
        byte[] plaintext = "Hello, Flora Sanctum!".getBytes();

        byte[] encrypted = AesGcmCipher.encrypt(plaintext, key);
        byte[] decrypted = AesGcmCipher.decrypt(encrypted, key);

        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void encryptEmptyPlaintext() {
        byte[] key = new byte[32];
        SecureRandomHolder.get().nextBytes(key);
        byte[] plaintext = new byte[0];

        byte[] encrypted = AesGcmCipher.encrypt(plaintext, key);
        byte[] decrypted = AesGcmCipher.decrypt(encrypted, key);

        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void encryptLargePlaintext() {
        byte[] key = new byte[32];
        SecureRandomHolder.get().nextBytes(key);
        byte[] plaintext = new byte[1024 * 1024]; // 1 MB
        SecureRandomHolder.get().nextBytes(plaintext);

        byte[] encrypted = AesGcmCipher.encrypt(plaintext, key);
        byte[] decrypted = AesGcmCipher.decrypt(encrypted, key);

        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void decryptWithWrongKeyFails() {
        byte[] key1 = new byte[32];
        SecureRandomHolder.get().nextBytes(key1);
        byte[] key2 = new byte[32];
        SecureRandomHolder.get().nextBytes(key2);
        byte[] plaintext = "secret data".getBytes();

        byte[] encrypted = AesGcmCipher.encrypt(plaintext, key1);
        assertThrows(CryptoException.class, () -> AesGcmCipher.decrypt(encrypted, key2));
    }

    @Test
    void decryptWithCorruptedDataFails() {
        byte[] key = new byte[32];
        SecureRandomHolder.get().nextBytes(key);
        byte[] plaintext = "secret data".getBytes();

        byte[] encrypted = AesGcmCipher.encrypt(plaintext, key);
        // Corrupt one byte in the ciphertext
        encrypted[AesGcmCipher.IV_LENGTH] ^= 0x01;

        assertThrows(CryptoException.class, () -> AesGcmCipher.decrypt(encrypted, key));
    }

    @Test
    void decryptTooShortDataFails() {
        byte[] key = new byte[32];
        SecureRandomHolder.get().nextBytes(key);
        byte[] tooShort = new byte[AesGcmCipher.IV_LENGTH + AesGcmCipher.GCM_TAG_LENGTH_BYTES - 1];

        assertThrows(CryptoException.class, () -> AesGcmCipher.decrypt(tooShort, key));
    }

    @Test
    void eachEncryptionUsesUniqueIv() {
        byte[] key = new byte[32];
        SecureRandomHolder.get().nextBytes(key);
        byte[] plaintext = "deterministic input".getBytes();

        Set<String> ivs = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            byte[] encrypted = AesGcmCipher.encrypt(plaintext, key);
            byte[] iv = java.util.Arrays.copyOf(encrypted, AesGcmCipher.IV_LENGTH);
            String ivHex = bytesToHex(iv);
            assertTrue(ivs.add(ivHex), "IV should be unique across encryptions");
        }
    }

    @Test
    void invalidEncryptKeyLength() {
        byte[] shortKey = new byte[16];
        byte[] plaintext = "test".getBytes();
        assertThrows(IllegalArgumentException.class,
                () -> AesGcmCipher.encrypt(plaintext, shortKey));
    }

    @Test
    void invalidDecryptKeyLength() {
        byte[] shortKey = new byte[16];
        // Need valid-length data to reach key length check
        byte[] validEncrypted = new byte[AesGcmCipher.IV_LENGTH + AesGcmCipher.GCM_TAG_LENGTH_BYTES + 1];
        assertThrows(IllegalArgumentException.class,
                () -> AesGcmCipher.decrypt(validEncrypted, shortKey));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}
