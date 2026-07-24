package com.flora.sanctum.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * AES-256-GCM 加解密。
 * <p>
 * 仅支持 12 字节 IV（推荐值），认证标签固定为 128 位（16 字节）。
 * 输出格式：{@code IV (12) || ciphertext || GCM tag (16)}。
 */
public final class AesGcmCipher {

    /** AES-GCM IV 长度（字节）。 */
    public static final int IV_LENGTH = 12;

    /** GCM 认证标签长度（位）。 */
    public static final int GCM_TAG_LENGTH_BITS = 128;

    /** GCM 认证标签长度（字节）。 */
    public static final int GCM_TAG_LENGTH_BYTES = 16;

    /** AES-256 密钥长度（字节）。 */
    public static final int KEY_LENGTH = 32;

    private static final String ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";

    private AesGcmCipher() {
    }

    /**
     * 加密明文。
     *
     * @param plaintext 明文
     * @param key       AES-256 密钥（32 字节）
     * @return IV || ciphertext || GCM tag
     * @throws CryptoException 如果加密失败
     */
    public static byte[] encrypt(byte[] plaintext, byte[] key) {
        if (key.length != KEY_LENGTH) {
            throw new IllegalArgumentException("key must be " + KEY_LENGTH + " bytes");
        }

        byte[] iv = SecureRandomHolder.get().generateSeed(IV_LENGTH);
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            SecretKeySpec keySpec = new SecretKeySpec(key, ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

            byte[] ciphertext = cipher.doFinal(plaintext);

            // 组装输出: IV || ciphertext
            byte[] output = new byte[IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, output, 0, IV_LENGTH);
            System.arraycopy(ciphertext, 0, output, IV_LENGTH, ciphertext.length);
            return output;
        } catch (NoSuchAlgorithmException e) {
            throw new CryptoException("AES/GCM algorithm not available", e);
        } catch (InvalidKeyException e) {
            throw new CryptoException("Invalid AES key", e);
        } catch (InvalidAlgorithmParameterException e) {
            throw new CryptoException("Invalid GCM parameters", e);
        } catch (Exception e) {
            throw new CryptoException("AES-GCM encryption failed", e);
        }
    }

    /**
     * 解密。
     *
     * @param encrypted IV || ciphertext || GCM tag（由 {@link #encrypt} 产生）
     * @param key       AES-256 密钥（32 字节）
     * @return 明文
     * @throws CryptoException 如果解密失败（包含认证失败或参数错误）
     */
    public static byte[] decrypt(byte[] encrypted, byte[] key) {
        if (encrypted.length < IV_LENGTH + GCM_TAG_LENGTH_BYTES) {
            throw new CryptoException("encrypted data too short: need at least " +
                    (IV_LENGTH + GCM_TAG_LENGTH_BYTES) + " bytes");
        }
        if (key.length != KEY_LENGTH) {
            throw new IllegalArgumentException("key must be " + KEY_LENGTH + " bytes");
        }

        byte[] iv = Arrays.copyOf(encrypted, IV_LENGTH);
        byte[] ciphertextWithTag = Arrays.copyOfRange(encrypted, IV_LENGTH, encrypted.length);

        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            SecretKeySpec keySpec = new SecretKeySpec(key, ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            return cipher.doFinal(ciphertextWithTag);
        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("AES-GCM decryption failed (wrong password or corrupted data)", e);
        }
    }

    /**
     * 生成随机 IV。
     */
    public static byte[] generateIv() {
        return SecureRandomHolder.get().generateSeed(IV_LENGTH);
    }
}
