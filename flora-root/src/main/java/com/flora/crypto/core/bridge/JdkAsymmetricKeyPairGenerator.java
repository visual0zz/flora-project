package com.flora.crypto.core.bridge;
import com.flora.tag.ThreadFragile;

import com.flora.crypto.core.keypair.AsymmetricCipherKeyPair;
import com.flora.crypto.core.interfaces.provider.AsymmetricCipherKeyPairGenerator;
import com.flora.crypto.core.keypair.AsymmetricKeyParameter;
import com.flora.crypto.core.param.KeyGenerationParameters;

import com.flora.java.CheckUtil;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

/**
 * 把 JDK 的 {@link KeyPairGenerator} 接入 {@link AsymmetricCipherKeyPairGenerator} 接口，
 * 返回本项目的轻量级 {@link AsymmetricCipherKeyPair}（持有 {@link AsymmetricKeyParameter}）。
 */
@ThreadFragile
public final class JdkAsymmetricKeyPairGenerator implements AsymmetricCipherKeyPairGenerator {

    private final String algorithm;
    private final KeyPairGenerator kpg;
    private int strength;
    private java.security.SecureRandom random;

    private JdkAsymmetricKeyPairGenerator(String algorithm) {
        this.algorithm = algorithm;
        try {
            this.kpg = KeyPairGenerator.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("不支持的密钥对算法: " + algorithm, e);
        }
    }

    public static JdkAsymmetricKeyPairGenerator of(String algorithm) {
        CheckUtil.notEmpty(algorithm, "算法名不能为空");
        return new JdkAsymmetricKeyPairGenerator(algorithm);
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

    @Override
    public void init(KeyGenerationParameters param) {
        CheckUtil.notNull(param, "密钥生成参数不能为空");
        this.strength = param.getStrength();
        this.random = param.getRandom();
        kpg.initialize(strength, random);
    }

    @Override
    public AsymmetricCipherKeyPair generateKeyPair() {
        KeyPair kp = kpg.generateKeyPair();
        return new AsymmetricCipherKeyPair(
                new AsymmetricKeyParameter(kp.getPublic()),
                new AsymmetricKeyParameter(kp.getPrivate()));
    }
}
