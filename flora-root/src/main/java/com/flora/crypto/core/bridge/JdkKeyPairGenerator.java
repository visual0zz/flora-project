package com.flora.crypto.core.bridge;
import com.flora.tag.ThreadFragile;

import com.flora.crypto.core.interfaces.provider.AlgorithmFamily;
import com.flora.java.CheckUtil;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;

/**
 * 把 JDK 自带的 {@link KeyPairGenerator} 接入统一的工厂式便捷封装。
 * <p>用于生成 RSA / EC 等非对称密钥对，配合 {@link JdkAsymmetricBlockCipher} 使用。
 * 实现 {@link AlgorithmFamily} 自述支持的算法名集合。</p>
 */
@ThreadFragile
public final class JdkKeyPairGenerator implements AlgorithmFamily {

    private final String algorithm;
    private final KeyPairGenerator kpg;

    private JdkKeyPairGenerator(String algorithm) {
        this.algorithm = algorithm;
        try {
            this.kpg = KeyPairGenerator.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("不支持的密钥对算法: " + algorithm, e);
        }
    }

    public static JdkKeyPairGenerator of(String algorithm) {
        CheckUtil.notEmpty(algorithm, "算法名不能为空");
        return new JdkKeyPairGenerator(algorithm);
    }

    public static final java.util.Set<String> SUPPORTED = java.util.Set.of(
            "RSA", "DSA", "DH", "EC", "X25519", "X448", "Ed25519", "Ed448");

    @Override
    public java.util.Set<String> supportedAlgorithms() {
        return SUPPORTED;
    }

    @Override
    public String getAlgorithmName() {
        return algorithm;
    }

    public KeyPair generate(int keySize) {
        kpg.initialize(keySize);
        return kpg.generateKeyPair();
    }

    public KeyPair generate() {
        return kpg.generateKeyPair();
    }

    /** 按算法特定参数生成（如 EC/X25519 的曲线参数）。 */
    public KeyPair generate(AlgorithmParameterSpec params) {
        CheckUtil.notNull(params, "算法参数不能为空");
        try {
            kpg.initialize(params);
        } catch (java.security.InvalidAlgorithmParameterException e) {
            throw new IllegalArgumentException("初始化密钥对生成器失败: " + algorithm, e);
        }
        return kpg.generateKeyPair();
    }
}
