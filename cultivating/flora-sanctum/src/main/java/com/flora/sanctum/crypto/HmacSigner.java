package com.flora.sanctum.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * HMAC-SHA256 签名工具。
 * <p>
 * 用于对明文存储区（meta.json）计算签名，防止篡改（如远程 URL 被恶意改写）。
 */
public final class HmacSigner {

    private static final String ALGORITHM = "HmacSHA256";
    /** HMAC-SHA256 输出长度（字节）。 */
    public static final int HMAC_LENGTH = 32;

    private HmacSigner() {
    }

    /**
     * 对数据进行 HMAC-SHA256 签名。
     *
     * @param data 待签名的数据
     * @param key  HMAC 密钥（32 字节）
     * @return 32 字节签名
     * @throws CryptoException 如果签名失败
     */
    public static byte[] sign(byte[] data, byte[] key) {
        if (key.length != 32) {
            throw new IllegalArgumentException("HMAC key must be 32 bytes");
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(key, ALGORITHM);
            mac.init(keySpec);
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException e) {
            throw new CryptoException("HMAC-SHA256 algorithm not available", e);
        } catch (InvalidKeyException e) {
            throw new CryptoException("Invalid HMAC key", e);
        }
    }

    /**
     * 验证 HMAC 签名是否匹配。
     *
     * @param data      待验证的数据
     * @param key       HMAC 密钥
     * @param signature 期望的签名
     * @return 签名匹配返回 true
     */
    public static boolean verify(byte[] data, byte[] key, byte[] signature) {
        byte[] expected = sign(data, key);
        return MessageDigest.isEqual(expected, signature);
    }
}
