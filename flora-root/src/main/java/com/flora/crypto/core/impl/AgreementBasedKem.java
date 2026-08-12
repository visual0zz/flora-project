package com.flora.crypto.core.impl;

import com.flora.common.register.AlgorithmComponent;
import com.flora.common.register.AlgorithmFactory;
import com.flora.common.register.AlgorithmFactoryRegister;
import com.flora.crypto.core.CryptoAlgorithmFactoryRegister;
import com.flora.crypto.core.constant.AsymmetricKeyType;
import com.flora.crypto.core.bridge.JdkAgreement;
import com.flora.crypto.core.bridge.JdkAsymmetricKeyPairGenerator;
import com.flora.crypto.core.interfaces.algorithm.Agreement;
import com.flora.crypto.core.interfaces.algorithm.DerivationFunction;
import com.flora.crypto.core.interfaces.algorithm.KeyEncapsulationMechanism;
import com.flora.crypto.core.interfaces.material.kem.Decapsulator;
import com.flora.crypto.core.interfaces.material.kem.Encapsulator;
import com.flora.crypto.core.interfaces.material.kem.SecretWithEncapsulation;
import com.flora.crypto.core.interfaces.material.param.AsymmetricPrivateKeyParameter;
import com.flora.crypto.core.interfaces.material.param.AsymmetricPublicKeyParameter;
import com.flora.crypto.core.interfaces.material.param.DerivationParameter;
import com.flora.crypto.core.param.HkdfParameters;
import com.flora.java.CheckUtil;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Set;

/**
 * 基于「密钥协商 + KDF」构造的真实 KEM（newcore）。
 * <p>封装：生成临时密钥对，用接收方公钥做密钥协商得到共享秘密 Z，再以 HKDF 派生固定长度对称密钥，
 * 把临时公钥编码作为封装密文。解封装：从封装密文重建临时公钥，做相同协商 + 派生，得到同一密钥。
 * 适用于经典曲线（ECDH / X25519 / DH 等）；后量子算法需另实现。</p>
 */
public final class AgreementBasedKem implements KeyEncapsulationMechanism {

    private static final int SECRET_LEN = 32;
    private static final byte[] KEM_INFO = "FLORA-KEM".getBytes();

    private final String kemAlgorithm;
    private final String agreementAlgorithm;
    private final String keyPairAlgorithm;
    private final AsymmetricKeyType keyType;

    private AgreementBasedKem(String kemAlgorithm, String agreementAlgorithm, String keyPairAlgorithm, AsymmetricKeyType keyType) {
        this.kemAlgorithm = kemAlgorithm;
        this.agreementAlgorithm = agreementAlgorithm;
        this.keyPairAlgorithm = keyPairAlgorithm;
        this.keyType = keyType;
    }

    public static AgreementBasedKem of(String agreementAlgorithm) {
        CheckUtil.notEmpty(agreementAlgorithm, "协商算法名不能为空");
        return switch (agreementAlgorithm) {
            case "ECDH", "ECDH-KEM" -> new AgreementBasedKem("ECDH-KEM", "ECDH", "EC", AsymmetricKeyType.EC);
            case "X25519", "X25519-KEM" -> new AgreementBasedKem("X25519-KEM", "X25519", "XDH", AsymmetricKeyType.CURVE25519);
            case "X448", "X448-KEM" -> new AgreementBasedKem("X448-KEM", "X448", "XDH", AsymmetricKeyType.CURVE25519);
            case "DH", "DH-KEM" -> new AgreementBasedKem("DH-KEM", "DH", "DH", AsymmetricKeyType.DH);
            default -> throw new IllegalArgumentException("不支持的协商算法: " + agreementAlgorithm);
        };
    }

    public static final Set<String> SUPPORTED = Set.of("ECDH-KEM", "X25519-KEM", "X448-KEM", "DH-KEM");

    @Override
    public String getAlgorithmName() {
        return kemAlgorithm;
    }

    @Override
    public Encapsulator newEncapsulator(AsymmetricPublicKeyParameter publicKey) {
        return new EncapsulatorImpl((AsymmetricKeyParameterImpl) publicKey);
    }

    @Override
    public Decapsulator newDecapsulator(AsymmetricPrivateKeyParameter privateKey) {
        return new DecapsulatorImpl((AsymmetricKeyParameterImpl) privateKey);
    }

    private byte[] deriveSecret(byte[] z) {
        DerivationFunction kdf = new HkdfDerivationFunction(com.flora.crypto.core.bridge.JdkMac.of("HmacSHA256"));
        kdf.init((DerivationParameter) new HkdfParameters(z, KEM_INFO));
        byte[] out = new byte[SECRET_LEN];
        kdf.generateBytes(out, 0, SECRET_LEN);
        return out;
    }

    private KeyPair generateEphemeral() {
        JdkAsymmetricKeyPairGenerator gen = JdkAsymmetricKeyPairGenerator.of(keyPairAlgorithm);
        gen.init(new KeyGenerationParameterImpl(256));
        return gen.generateKeyPairJdk();
    }

    private final class EncapsulatorImpl implements Encapsulator {
        private final AsymmetricKeyParameterImpl recipientPub;

        EncapsulatorImpl(AsymmetricKeyParameterImpl recipientPub) {
            this.recipientPub = recipientPub;
        }

        @Override
        public SecretWithEncapsulation encapsulate() {
            KeyPair eph = generateEphemeral();
            Agreement a = JdkAgreement.of(agreementAlgorithm);
            a.init(AsymmetricKeyParameterImpl.fromPrivate(eph.getPrivate()));
            byte[] z = a.calculateAgreement(recipientPub);
            byte[] secret = deriveSecret(z);
            return new SecretWithEncapsulationImpl(secret, eph.getPublic().getEncoded());
        }

        @Override
        public com.flora.crypto.core.interfaces.material.param.CipherParameter getPublicKey() {
            return recipientPub;
        }
    }

    private final class DecapsulatorImpl implements Decapsulator {
        private final AsymmetricKeyParameterImpl recipientPriv;

        DecapsulatorImpl(AsymmetricKeyParameterImpl recipientPriv) {
            this.recipientPriv = recipientPriv;
        }

        @Override
        public byte[] decapsulate(byte[] encapsulation) {
            CheckUtil.notNull(encapsulation, "封装密文不能为空");
            PublicKey ephPub = rebuildPublicKey(encapsulation);
            Agreement a = JdkAgreement.of(agreementAlgorithm);
            a.init(recipientPriv);
            byte[] z = a.calculateAgreement(AsymmetricKeyParameterImpl.fromPublic(ephPub));
            return deriveSecret(z);
        }

        @Override
        public AsymmetricPrivateKeyParameter getPrivateKey() {
            return recipientPriv;
        }
    }

    /** 从封装密文（X.509 编码）重建 JDK 公钥（decapsulate 路径：原始字节需还原为 JDK Key）。 */
    private PublicKey rebuildPublicKey(byte[] encoded) {
        try {
            KeyFactory kf = KeyFactory.getInstance(keyPairAlgorithm);
            return kf.generatePublic(new X509EncodedKeySpec(encoded));
        } catch (Exception e) {
            throw new IllegalStateException("重建临时公钥失败: " + keyPairAlgorithm, e);
        }
    }

    @Override
    public AlgorithmFactory<? extends KeyEncapsulationMechanism> factory() {
        return FACTORY;
    }

    public static final AlgorithmFactory<KeyEncapsulationMechanism> FACTORY = new AlgorithmFactory<>() {
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
        public KeyEncapsulationMechanism construct(String algorithmName, AlgorithmComponent... components) {
            CheckUtil.notNull(algorithmName, "算法名不能为空");
            return AgreementBasedKem.of(algorithmName);
        }
    };
}
