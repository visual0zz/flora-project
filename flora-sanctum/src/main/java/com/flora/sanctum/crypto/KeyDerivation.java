package com.flora.sanctum.crypto;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;

/**
 * PBKDF2-HMAC-SHA256 密钥派生。
 * <p>
 * 将主密码 + salt + 迭代次数派生为 64 字节密钥材料，前 32 字节用于 AES-256-GCM
 * 加密，后 32 字节用于明文区 HMAC-SHA256 签名（防篡改）。
 */
public final class KeyDerivation {

    /** 派生密钥材料的总长度（加密 32 + MAC 32）。 */
    public static final int DERIVED_KEY_LENGTH = 64;

    /** AES-256 密钥长度（字节）。 */
    public static final int ENCRYPTION_KEY_LENGTH = 32;

    /** HMAC 密钥长度（字节）。 */
    public static final int MAC_KEY_LENGTH = 32;

    /** 最小迭代次数。 */
    public static final int MIN_ITERATIONS = 600_000;

    /** 盐的最小长度（字节）。 */
    public static final int MIN_SALT_LENGTH = 16;

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    private KeyDerivation() {
    }

    /**
     * 派生 64 字节密钥材料。
     *
     * @param password   主密码
     * @param salt       盐值（至少 {@link #MIN_SALT_LENGTH} 字节）
     * @param iterations 迭代次数（至少 {@link #MIN_ITERATIONS}）
     * @return 64 字节密钥材料
     * @throws IllegalArgumentException 如果参数不满足最小要求
     * @throws CryptoException          如果派生过程失败
     */
    public static byte[] derive(char[] password, byte[] salt, int iterations) {
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("password must not be null or empty");
        }
        if (salt == null || salt.length < MIN_SALT_LENGTH) {
            throw new IllegalArgumentException("salt must be at least " + MIN_SALT_LENGTH + " bytes");
        }
        if (iterations < MIN_ITERATIONS) {
            throw new IllegalArgumentException("iterations must be at least " + MIN_ITERATIONS);
        }

        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, DERIVED_KEY_LENGTH * 8);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] derived = factory.generateSecret(spec).getEncoded();
            spec.clearPassword();
            return derived;
        } catch (NoSuchAlgorithmException e) {
            throw new CryptoException("PBKDF2 algorithm not available: " + ALGORITHM, e);
        } catch (InvalidKeySpecException e) {
            throw new CryptoException("Invalid key specification for PBKDF2", e);
        } finally {
            spec.clearPassword();
        }
    }

    /**
     * 从密钥材料中提取加密密钥（前 32 字节）。
     */
    public static byte[] encryptionKey(byte[] derivedKeyMaterial) {
        return Arrays.copyOfRange(derivedKeyMaterial, 0, ENCRYPTION_KEY_LENGTH);
    }

    /**
     * 从密钥材料中提取 MAC 密钥（后 32 字节）。
     */
    public static byte[] macKey(byte[] derivedKeyMaterial) {
        return Arrays.copyOfRange(derivedKeyMaterial, ENCRYPTION_KEY_LENGTH, DERIVED_KEY_LENGTH);
    }
}
