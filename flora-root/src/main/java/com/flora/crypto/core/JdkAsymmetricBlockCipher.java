package com.flora.crypto.core;

import com.flora.java.CheckUtil;

import javax.crypto.Cipher;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

/**
 * 把 JDK 自带的 {@link Cipher}（非对称，如 RSA）接入 {@link AsymmetricBlockCipher} 接口。
 * <p>示例：{@code CryptoProvider.asymmetricCipher("RSA/ECB/PKCS1Padding")} 或 {@code JdkAsymmetricBlockCipher.rsa()}。</p>
 */
public final class JdkAsymmetricBlockCipher implements AsymmetricBlockCipher {

    private final String transformation;
    private final Cipher cipher;
    private int keyBitLength;

    private JdkAsymmetricBlockCipher(String transformation) {
        this.transformation = transformation;
        try {
            this.cipher = Cipher.getInstance(transformation);
        } catch (NoSuchAlgorithmException | javax.crypto.NoSuchPaddingException e) {
            throw new IllegalArgumentException("不支持的非对称变换: " + transformation, e);
        }
    }

    public static JdkAsymmetricBlockCipher of(String transformation) {
        CheckUtil.notEmpty(transformation, "变换字符串不能为空");
        return new JdkAsymmetricBlockCipher(transformation);
    }

    public static JdkAsymmetricBlockCipher rsa() {
        return of("RSA/ECB/PKCS1Padding");
    }

    @Override
    public void init(boolean forEncryption, CipherParameters params) {
        CheckUtil.notNull(params, "参数不能为空");
        Key key = CipherSupport.asymmetricKeyParameter(params).getKey();
        this.keyBitLength = rsaBitLength(key);
        try {
            cipher.init(forEncryption ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, key);
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("初始化非对称密码失败: " + transformation, e);
        }
    }

    @Override
    public String getAlgorithmName() {
        return transformation;
    }

    @Override
    public int getInputBlockSize() {
        int keyBytes = keyBitLength / 8;
        if (transformation.contains("OAEP")) {
            return keyBytes - 2 - 2 * 20; // 默认 SHA-1 OAEP 开销
        }
        if (transformation.contains("PKCS1")) {
            return keyBytes - 11;
        }
        return keyBytes;
    }

    @Override
    public int getOutputBlockSize() {
        return keyBitLength / 8;
    }

    @Override
    public byte[] processBlock(byte[] in, int inOff, int len) {
        try {
            return cipher.doFinal(in, inOff, len);
        } catch (Exception e) {
            throw new IllegalStateException("处理非对称块失败: " + transformation, e);
        }
    }

    private static int rsaBitLength(Key key) {
        if (key instanceof RSAPublicKey) {
            return ((RSAPublicKey) key).getModulus().bitLength();
        }
        if (key instanceof RSAPrivateKey) {
            return ((RSAPrivateKey) key).getModulus().bitLength();
        }
        if (key instanceof RSAKey) {
            return ((RSAKey) key).getModulus().bitLength();
        }
        return 0;
    }
}
