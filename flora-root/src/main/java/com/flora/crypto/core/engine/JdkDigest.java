package com.flora.crypto.core.engine;
import com.flora.tag.ThreadFragile;
import com.flora.crypto.core.interfaces.provider.Digest;
import com.flora.crypto.core.interfaces.provider.ExtendedDigest;

import com.flora.java.CheckUtil;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 把 JDK 自带的 {@link MessageDigest} 接入 {@link ExtendedDigest} 接口。
 * <p>示例：{@code CryptoProvider.digest("SHA-256")} 或 {@code JdkDigest.of("SHA-512")}。</p>
 */
@ThreadFragile
public final class JdkDigest implements ExtendedDigest {

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

    private static final java.util.Set<String> SUPPORTED = java.util.Set.of(
            "MD5", "SHA-1", "SHA-224", "SHA-256", "SHA-384", "SHA-512",
            "SHA-512/224", "SHA-512/256", "SHA3-224", "SHA3-256", "SHA3-384", "SHA3-512");

    @Override
    public java.util.Set<String> supportedAlgorithms() {
        return SUPPORTED;
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
    public int getByteLength() {
        return byteLengthOf(algorithm);
    }

    /**
     * JDK 不暴露摘要内部块长度，按算法名给出已知值；未知算法返回 0。
     */
    private static int byteLengthOf(String algorithm) {
        return switch (algorithm) {
            case "MD5", "SHA", "SHA-1", "SHA-224", "SHA-256" -> 64;
            case "SHA-384", "SHA-512", "SHA-512/224", "SHA-512/256" -> 128;
            case "SHA3-224" -> 144;
            case "SHA3-256" -> 136;
            case "SHA3-384" -> 104;
            case "SHA3-512" -> 72;
            default -> 0;
        };
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
