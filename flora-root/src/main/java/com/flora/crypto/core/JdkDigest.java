package com.flora.crypto.core;

import com.flora.java.CheckUtil;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 把 JDK 自带的 {@link MessageDigest} 接入 {@link Digest} 接口。
 * <p>示例：{@code CryptoProvider.digest("SHA-256")} 或 {@code JdkDigest.of("SHA-512")}。</p>
 */
public final class JdkDigest implements Digest {

    private final String algorithm;
    private final MessageDigest md;

    private JdkDigest(String algorithm) {
        this.algorithm = algorithm;
        try {
            this.md = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("不支持的摘要算法: " + algorithm, e);
        }
    }

    public static JdkDigest of(String algorithm) {
        CheckUtil.notEmpty(algorithm, "算法名不能为空");
        return new JdkDigest(algorithm);
    }

    @Override
    public String getAlgorithmName() {
        return algorithm;
    }

    @Override
    public int getDigestSize() {
        return md.getDigestLength();
    }

    @Override
    public void update(byte in) {
        md.update(in);
    }

    @Override
    public void update(byte[] in, int inOff, int len) {
        md.update(in, inOff, len);
    }

    @Override
    public int doFinal(byte[] out, int outOff) {
        try {
            return md.digest(out, outOff, out.length - outOff);
        } catch (java.security.DigestException e) {
            throw new IllegalStateException("摘要计算失败: " + algorithm, e);
        }
    }

    @Override
    public void reset() {
        md.reset();
    }
}
