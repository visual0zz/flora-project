package com.flora.crypto.core.bridge;

import com.flora.tag.ThreadFragile;

import com.flora.common.algorithm.AlgorithmComponent;
import com.flora.common.algorithm.AlgorithmFactory;
import com.flora.common.algorithm.AlgorithmFamilyRegister;
import com.flora.crypto.core.CryptoAlgorithmFamilyRegister;
import com.flora.crypto.core.impl.AsymmetricKeyParameterImpl;
import com.flora.crypto.core.interfaces.algorithm.Signature;
import com.flora.crypto.core.interfaces.material.param.CipherParameter;
import com.flora.java.CheckUtil;

import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.util.Set;

/**
 * 把 JDK 自带的 {@link java.security.Signature} 接入 newcore {@link Signature} 接口。
 * <p>覆盖 SSH 所需签名套件：RSA（SHA-1/224/256/384/512）、DSA、ECDSA（P-256/384/521）
 * 与 EdDSA（Ed25519/Ed448）。{@code sign}/{@code verify} 直接对已完成摘要计算的字节签名/验签。</p>
 */
@ThreadFragile
public final class JdkSignature implements Signature {

    private final String algorithm;
    private final java.security.Signature signature;

    private JdkSignature(String algorithm) {
        this.algorithm = algorithm;
        try {
            this.signature = java.security.Signature.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("不支持的签名算法: " + algorithm, e);
        }
    }

    public static JdkSignature of(String algorithm) {
        CheckUtil.notEmpty(algorithm, "算法名不能为空");
        return new JdkSignature(algorithm);
    }

    private static final Set<String> SUPPORTED = Set.of(
            "SHA1withRSA", "SHA224withRSA", "SHA256withRSA", "SHA384withRSA", "SHA512withRSA",
            "SHA1withDSA", "NONEwithDSA",
            "SHA256withECDSA", "SHA384withECDSA", "SHA512withECDSA",
            "EdDSA", "Ed25519", "Ed448");

    @Override
    public String getAlgorithmName() {
        return algorithm;
    }

    @Override
    public void init(boolean forSigning, CipherParameter params) {
        CheckUtil.notNull(params, "密钥参数不能为空");
        if (!(params instanceof AsymmetricKeyParameterImpl)) {
            throw new IllegalArgumentException("签名需要 AsymmetricKeyParameterImpl");
        }
        Key key = ((AsymmetricKeyParameterImpl) params).getJdkKey();
        try {
            if (forSigning) {
                if (!(key instanceof PrivateKey)) {
                    throw new IllegalArgumentException("签名需要私钥");
                }
                signature.initSign((PrivateKey) key);
            } else {
                if (!(key instanceof PublicKey)) {
                    throw new IllegalArgumentException("验签需要公钥");
                }
                signature.initVerify((PublicKey) key);
            }
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("初始化签名失败: " + algorithm, e);
        }
    }

    @Override
    public byte[] sign(byte[] digest) {
        CheckUtil.notNull(digest, "摘要不能为空");
        try {
            signature.update(digest);
            return signature.sign();
        } catch (SignatureException e) {
            throw new IllegalStateException("签名失败: " + algorithm, e);
        }
    }

    @Override
    public boolean verify(byte[] digest, byte[] signatureBytes) {
        CheckUtil.notNull(digest, "摘要不能为空");
        CheckUtil.notNull(signatureBytes, "签名不能为空");
        try {
            this.signature.update(digest);
            return this.signature.verify(signatureBytes);
        } catch (SignatureException e) {
            throw new IllegalStateException("验签失败: " + algorithm, e);
        }
    }

    @Override
    public AlgorithmFactory<? extends Signature> factory() {
        return FACTORY;
    }

    public static final AlgorithmFactory<Signature> FACTORY = new AlgorithmFactory<>() {
        @Override
        public Class<? extends AlgorithmFamilyRegister> registerTo() {
            return CryptoAlgorithmFamilyRegister.class;
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
        public Signature construct(String algorithmName, AlgorithmComponent... components) {
            CheckUtil.notNull(algorithmName, "算法名不能为空");
            return JdkSignature.of(algorithmName);
        }
    };
}
