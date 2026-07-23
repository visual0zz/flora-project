package com.flora.crypto;

import com.flora.tag.WorkInProgress;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 加密工具枚举，提供 AES-GCM 加密/解密功能。
 * <p>当前实现使用 {@value #AES} 算法，加密结果前置 12 字节随机 IV。
 * 标记为 {@link WorkInProgress}，后续可能扩展更多算法。</p>
 * <p>使用方式：
 * <pre>{@code
 * CryptoResult result = CryptoUtil.AES.withKey(key).encrypt(data);
 * String encrypted = result.toBase64();
 * }</pre></p>
 */
@WorkInProgress
public enum CryptoUtil {
    AES(new Crypto() {
        @Override
        public byte[] encrypt(byte[] data, byte[] key) throws Exception {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);

            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(16 * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

            byte[] ciphertext = cipher.doFinal(data);

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);

            return buffer.array();
        }

        @Override
        public byte[] decrypt(byte[] data, byte[] key) throws Exception {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            byte[] iv = new byte[12];
            buffer.get(iv);

            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(16 * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            return cipher.doFinal(ciphertext);
        }
    });
    private final Crypto crypto;

    CryptoUtil(Crypto crypto) {
        this.crypto = crypto;
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 使用字节数组密钥创建 KeyHolder。
     *
     * @param key 密钥字节数组
     * @return KeyHolder 实例
     */
    public KeyHolder withKey(byte[] key) {
        return new KeyHolder(key, this.crypto);
    }

    /**
     * 使用字符串密钥创建 KeyHolder（UTF-8 编码）。
     *
     * @param key 密钥字符串
     * @return KeyHolder 实例
     */
    public KeyHolder withKey(String key) {
        return new KeyHolder(key.getBytes(StandardCharsets.UTF_8), this.crypto);
    }

    /**
     * 加密密钥持有者，绑定密钥后提供加密/解密操作。
     */
    public static class KeyHolder {
        private final byte[] key;
        private final Crypto crypto;

        private KeyHolder(byte[] key, Crypto crypto) {
            this.key = key;
            this.crypto = crypto;
        }

        /**
         * 加密字节数据。
         *
         * @param data 明文数据
         * @return 加密结果
         * @throws Exception 加密过程中可能抛出的异常
         */
        public CryptoResult encrypt(byte[] data) throws Exception {
            return new CryptoResult(crypto.encrypt(data, key));
        }

        /**
         * 加密字符串（UTF-8 编码）。
         *
         * @param data 明文字符串
         * @return 加密结果
         * @throws Exception 加密过程中可能抛出的异常
         */
        public CryptoResult encrypt(String data) throws Exception {
            return encrypt(data.getBytes(StandardCharsets.UTF_8));
        }

        /**
         * 解密字节数据。
         *
         * @param data 密文数据
         * @return 解密结果
         * @throws Exception 解密过程中可能抛出的异常
         */
        public CryptoResult decrypt(byte[] data) throws Exception {
            return new CryptoResult(crypto.decrypt(data, key));
        }

        /**
         * 解密 Base64 编码的密文字符串。
         *
         * @param data Base64 编码的密文
         * @return 解密结果
         * @throws Exception 解密过程中可能抛出的异常
         */
        public CryptoResult decryptBase64(String data) throws Exception {
            return decrypt(Base64.getDecoder().decode(data));
        }
    }

    /**
     * 加密结果，提供 byte 数组、Base64 字符串和普通字符串的获取方式。
     */
    public static class CryptoResult {
        private final byte[] data;

        private CryptoResult(byte[] data) {
            this.data = data;
        }

        public String toBase64() {
            return Base64.getEncoder().encodeToString(data);
        }

        public byte[] toByteArray() {
            return data;
        }

        @Override
        public String toString() {
            return new String(data, StandardCharsets.UTF_8);
        }
    }

    private interface Crypto {
        byte[] encrypt(byte[] data, byte[] key) throws Exception;

        byte[] decrypt(byte[] data, byte[] key) throws Exception;
    }

}
