package com.flora.crypto.core.impl;

import com.flora.crypto.core.interfaces.provider.Agreement;
import com.flora.crypto.core.keypair.AsymmetricKeyParameter;
import com.flora.crypto.core.interfaces.CipherParameters;
import com.flora.crypto.core.CryptoProvider;
import com.flora.crypto.core.interfaces.Decapsulator;
import com.flora.crypto.core.interfaces.provider.DerivationFunction;
import com.flora.crypto.core.interfaces.Encapsulator;
import com.flora.crypto.core.param.HkdfParameters;
import com.flora.crypto.core.interfaces.provider.KEM;
import com.flora.crypto.core.interfaces.SecretWithEncapsulation;
import com.flora.crypto.core.impl.SecretWithEncapsulationImpl;

import com.flora.java.CheckUtil;

import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyFactory;
import java.security.SecureRandom;
import java.security.interfaces.ECKey;
import java.security.spec.X509EncodedKeySpec;

/**
 * 基于「密钥协商 + KDF」构造的真实 KEM（Bouncy Castle 风格）。
 * <p>封装：生成临时密钥对，用接收方公钥做密钥协商得到共享秘密 Z，再以 HKDF 派生固定长度对称密钥，
 * 把临时公钥编码作为封装密文。解封装：从封装密文重建临时公钥，做相同协商 + 派生，得到同一密钥。
 * 适用于经典曲线（ECDH / X25519 / DH 等）；后量子算法需另实现。</p>
 */
public final class AgreementBasedKem implements KEM {

    private static final int SECRET_LEN = 32;
    private static final byte[] KEM_INFO = "FLORA-KEM".getBytes();

    private final String agreementAlgorithm;
    private final SecureRandom random = new SecureRandom();

    private AgreementBasedKem(String agreementAlgorithm) {
        this.agreementAlgorithm = agreementAlgorithm;
    }

    public static AgreementBasedKem of(String agreementAlgorithm) {
        CheckUtil.notEmpty(agreementAlgorithm, "协商算法名不能为空");
        return new AgreementBasedKem(agreementAlgorithm);
    }

    public static final java.util.Set<String> SUPPORTED = java.util.Set.of("ECDH", "X25519", "X448", "DH");

    @Override
    public java.util.Set<String> supportedAlgorithms() {
        return SUPPORTED;
    }

    @Override
    public String getAlgorithmName() {
        return agreementAlgorithm;
    }

    @Override
    public Encapsulator newEncapsulator(CipherParameters publicKey) {
        return new EncapsulatorImpl(asAsym(publicKey));
    }

    @Override
    public Decapsulator newDecapsulator(CipherParameters privateKey) {
        return new DecapsulatorImpl(asAsym(privateKey));
    }

    private byte[] deriveSecret(byte[] z) {
        DerivationFunction kdf = CryptoProvider.derivationFunction("HKDF(HmacSHA256)");
        kdf.init(new HkdfParameters(z, KEM_INFO));
        byte[] out = new byte[SECRET_LEN];
        kdf.generateBytes(out, 0, SECRET_LEN);
        return out;
    }

    private KeyPair generateEphemeral(Key recipientKey) {
        try {
            // XEC 密钥（X25519/X448）的 getAlgorithm() 返回 "XDH"，但 params 带具体曲线名
            if (recipientKey instanceof java.security.interfaces.XECKey xec) {
                String curve = ((java.security.spec.NamedParameterSpec) xec.getParams()).getName();
                return CryptoProvider.keyPairGenerator(curve).generate(xec.getParams());
            }
            if (recipientKey instanceof ECKey ec) {
                return CryptoProvider.keyPairGenerator("EC").generate(ec.getParams());
            }
            if (recipientKey instanceof javax.crypto.interfaces.DHKey dh) {
                return CryptoProvider.keyPairGenerator("DH").generate(dh.getParams());
            }
            return CryptoProvider.keyPairGenerator(recipientKey.getAlgorithm()).generate(2048);
        } catch (Exception e) {
            throw new IllegalStateException("生成临时密钥失败: " + agreementAlgorithm, e);
        }
    }

    private static AsymmetricKeyParameter asAsym(CipherParameters params) {
        if (params instanceof AsymmetricKeyParameter) {
            return (AsymmetricKeyParameter) params;
        }
        throw new IllegalArgumentException("KEM 需要 AsymmetricKeyParameter");
    }

    private final class EncapsulatorImpl implements Encapsulator {
        private final AsymmetricKeyParameter recipientPub;

        EncapsulatorImpl(AsymmetricKeyParameter recipientPub) {
            this.recipientPub = recipientPub;
        }

        @Override
        public int getEncapsulationLength() {
            return recipientPub.getKey().getEncoded().length;
        }

        @Override
        public int getSecretLength() {
            return SECRET_LEN;
        }

        @Override
        public SecretWithEncapsulation encapsulate() {
            KeyPair eph = generateEphemeral(recipientPub.getKey());
            Agreement a = CryptoProvider.agreement(agreementAlgorithm);
            a.init(new AsymmetricKeyParameter(eph.getPrivate()));
            byte[] z = a.calculateAgreement(recipientPub);
            byte[] secret = deriveSecret(z);
            return new SecretWithEncapsulationImpl(secret, eph.getPublic().getEncoded());
        }
    }

    private final class DecapsulatorImpl implements Decapsulator {
        private final AsymmetricKeyParameter recipientPriv;
        private final int encapsulationLength;

        DecapsulatorImpl(AsymmetricKeyParameter recipientPriv) {
            this.recipientPriv = recipientPriv;
            // 封装密文长度 = 同曲线临时公钥编码长度
            this.encapsulationLength = generateEphemeral(recipientPriv.getKey()).getPublic().getEncoded().length;
        }

        @Override
        public int getEncapsulationLength() {
            return encapsulationLength;
        }

        @Override
        public int getSecretLength() {
            return SECRET_LEN;
        }

        @Override
        public SecretWithEncapsulation decapsulate(byte[] encapsulation) {
            try {
                KeyFactory kf = KeyFactory.getInstance(recipientPriv.getKey().getAlgorithm());
                Key ephPub = kf.generatePublic(new X509EncodedKeySpec(encapsulation));
                Agreement a = CryptoProvider.agreement(agreementAlgorithm);
                a.init(recipientPriv);
                byte[] z = a.calculateAgreement(new AsymmetricKeyParameter(ephPub));
                byte[] secret = deriveSecret(z);
                return new SecretWithEncapsulationImpl(secret, encapsulation);
            } catch (Exception e) {
                throw new IllegalStateException("KEM 解封装失败: " + agreementAlgorithm, e);
            }
        }
    }
}
