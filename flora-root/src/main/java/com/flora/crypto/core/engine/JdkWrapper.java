package com.flora.crypto.core.engine;

import com.flora.crypto.core.AsymmetricKeyParameter;
import com.flora.crypto.core.CipherParameters;
import com.flora.crypto.core.KeyParameter;
import com.flora.crypto.core.Wrapper;

import com.flora.java.CheckUtil;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.Key;

/**
 * 把 JDK 的密钥包装能力接入 {@link Wrapper} 接口（对称密钥包装，AESWrap 等）。
 * <p>JDK 的 {@code Cipher.wrap/unwrap} 要求传入 {@code Key} 对象；本适配器把「待包装的字节」
 * 与「包装密钥」都按 JCE 算法名构造为 {@code SecretKeySpec}，从而以字节数组形态完成包装/解包。</p>
 */
public final class JdkWrapper implements Wrapper {

    private final String algorithm;
    private final Cipher cipher;
    private Key wrappingKey;

    private JdkWrapper(String algorithm) {
        this.algorithm = algorithm;
        try {
            this.cipher = Cipher.getInstance(algorithm);
        } catch (Exception e) {
            throw new IllegalArgumentException("不支持的密钥包装算法: " + algorithm, e);
        }
    }

    public static JdkWrapper of(String algorithm) {
        CheckUtil.notEmpty(algorithm, "算法名不能为空");
        return new JdkWrapper(algorithm);
    }

    @Override
    public void init(boolean forWrapping, CipherParameters params) {
        CheckUtil.notNull(params, "包装密钥参数不能为空");
        this.wrappingKey = keyOf(params);
        int mode = forWrapping ? Cipher.WRAP_MODE : Cipher.UNWRAP_MODE;
        try {
            cipher.init(mode, wrappingKey);
        } catch (Exception e) {
            throw new IllegalArgumentException("初始化密钥包装失败: " + algorithm, e);
        }
    }

    @Override
    public String getAlgorithmName() {
        return algorithm;
    }

    @Override
    public byte[] wrap(byte[] in, int inOff, int len) {
        try {
            SecretKeySpec toWrap = new SecretKeySpec(slice(in, inOff, len), algorithm);
            return cipher.wrap(toWrap);
        } catch (Exception e) {
            throw new IllegalStateException("密钥包装失败: " + algorithm, e);
        }
    }

    @Override
    public byte[] unwrap(byte[] in, int inOff, int len) {
        try {
            Key key = cipher.unwrap(slice(in, inOff, len), algorithm, Cipher.SECRET_KEY);
            return key.getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("密钥解包失败: " + algorithm, e);
        }
    }

    private Key keyOf(CipherParameters params) {
        if (params instanceof KeyParameter) {
            return new SecretKeySpec(((KeyParameter) params).getKey(), algorithm);
        }
        if (params instanceof AsymmetricKeyParameter) {
            return ((AsymmetricKeyParameter) params).getKey();
        }
        throw new IllegalArgumentException("需要 KeyParameter 或 AsymmetricKeyParameter");
    }

    private static byte[] slice(byte[] in, int inOff, int len) {
        byte[] out = new byte[len];
        System.arraycopy(in, inOff, out, 0, len);
        return out;
    }
}
