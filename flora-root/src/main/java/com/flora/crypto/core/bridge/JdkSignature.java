package com.flora.crypto.core.bridge;

import com.flora.tag.ThreadFragile;
import com.flora.crypto.core.interfaces.provider.AlgorithmFamily;

import com.flora.java.CheckUtil;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Set;

/**
 * 把 JDK 自带的 {@link Signature} 接入 flora 的算法族接口。
 * <p>覆盖 SSH 所需签名套件：RSA（SHA-1/224/256/384/512）、DSA、ECDSA（P-256/384/521）
 * 与 EdDSA（Ed25519/Ed448）。当前 flora 尚无「签名/验签」引擎接口，本类作为 JDK 原生能力的
 * 转发实现存在，供 {@code com.flora.comm.ssh.crypto} 适配层调用。</p>
 */
@ThreadFragile
public final class JdkSignature implements AlgorithmFamily {

    private final String algorithm;
    private final Signature signature;

    private JdkSignature(String algorithm) {
        this.algorithm = algorithm;
        try {
            this.signature = Signature.getInstance(algorithm);
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
    public Set<String> supportedAlgorithms() {
        return SUPPORTED;
    }

    @Override
    public String getAlgorithmName() {
        return algorithm;
    }

    /** 初始化验签。 */
    public void initVerify(PublicKey publicKey) {
        CheckUtil.notNull(publicKey, "公钥不能为空");
        try {
            signature.initVerify(publicKey);
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("初始化验签失败: " + algorithm, e);
        }
    }

    /** 初始化签名。 */
    public void initSign(PrivateKey privateKey) {
        CheckUtil.notNull(privateKey, "私钥不能为空");
        try {
            signature.initSign(privateKey);
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("初始化签名失败: " + algorithm, e);
        }
    }

    /** 喂入待签名/验签数据。 */
    public void update(byte[] data) {
        CheckUtil.notNull(data, "数据不能为空");
        try {
            signature.update(data);
        } catch (SignatureException e) {
            throw new IllegalStateException("签名数据更新失败: " + algorithm, e);
        }
    }

    /** 喂入待签名/验签数据（带偏移）。 */
    public void update(byte[] data, int off, int len) {
        CheckUtil.notNull(data, "数据不能为空");
        try {
            signature.update(data, off, len);
        } catch (SignatureException e) {
            throw new IllegalStateException("签名数据更新失败: " + algorithm, e);
        }
    }

    /** 生成签名。 */
    public byte[] sign() {
        try {
            return signature.sign();
        } catch (SignatureException e) {
            throw new IllegalStateException("签名失败: " + algorithm, e);
        }
    }

    /** 校验签名。 */
    public boolean verify(byte[] sig) {
        CheckUtil.notNull(sig, "签名不能为空");
        try {
            return signature.verify(sig);
        } catch (SignatureException e) {
            throw new IllegalStateException("验签失败: " + algorithm, e);
        }
    }
}
