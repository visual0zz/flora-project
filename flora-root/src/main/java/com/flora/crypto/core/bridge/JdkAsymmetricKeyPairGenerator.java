package com.flora.crypto.core.bridge;

import com.flora.common.register.AlgorithmFactoryRegister;
import com.flora.tag.ThreadFragile;

import com.flora.common.register.AlgorithmComponent;
import com.flora.common.register.AlgorithmFactory;
import com.flora.crypto.core.CryptoAlgorithmFactoryRegister;
import com.flora.crypto.core.impl.AsymmetricKeyParameterImpl;
import com.flora.crypto.core.impl.KeyGenerationParameterImpl;
import com.flora.crypto.core.interfaces.algorithm.AsymmetricCipherKeyPairGenerator;
import com.flora.crypto.core.interfaces.material.keypair.AsymmetricCipherKeyPair;
import com.flora.crypto.core.interfaces.material.param.KeyGenerationParameter;
import com.flora.java.CheckUtil;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

/**
 * 把 JDK 的 {@link KeyPairGenerator} 接入 newcore {@link AsymmetricCipherKeyPairGenerator} 接口，
 * 返回持有 {@link AsymmetricKeyParameterImpl} 的 {@link AsymmetricCipherKeyPair}。
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
    public String getAlgorithmName() {
        return algorithm;
    }

    @Override
    public void init(KeyGenerationParameter param) {
        CheckUtil.notNull(param, "密钥生成参数不能为空");
        this.strength = param.getStrength();
        this.random = param instanceof KeyGenerationParameterImpl impl
                ? impl.getRandom() : new java.security.SecureRandom();
        kpg.initialize(strength, random);
    }

    @Override
    public AsymmetricCipherKeyPair generateKeyPair() {
        KeyPair kp = kpg.generateKeyPair();
        return new com.flora.crypto.core.impl.AsymmetricCipherKeyPairImpl(
                AsymmetricKeyParameterImpl.fromPublic(kp.getPublic()),
                AsymmetricKeyParameterImpl.fromPrivate(kp.getPrivate()));
    }

    /** 直接返回 JDK 密钥对（供 KEM 等需要底层 {@link Key} 的桥接层使用）。 */
    public KeyPair generateKeyPairJdk() {
        return kpg.generateKeyPair();
    }

    @Override
    public AlgorithmFactory<? extends AsymmetricCipherKeyPairGenerator> factory() {
        return FACTORY;
    }

    public static final AlgorithmFactory<AsymmetricCipherKeyPairGenerator> FACTORY =
            new AlgorithmFactory<>() {
                @Override
                public Class<? extends AlgorithmFactoryRegister> registerTo() {
                    return CryptoAlgorithmFactoryRegister.class;
                }

                @Override
                public Set<String> supportedAlgorithms() {
                    return SUPPORTED;
                }

                @Override
                public int priority() {
                    return 0;
                }

                @Override
                public Class<AlgorithmComponent>[] componentTypes() {
                    return new Class[0];
                }

                @Override
                public AsymmetricCipherKeyPairGenerator construct(String algorithmName,
                        AlgorithmComponent... components) {
                    CheckUtil.notNull(algorithmName, "算法名不能为空");
                    return JdkAsymmetricKeyPairGenerator.of(algorithmName);
                }
            };
}
