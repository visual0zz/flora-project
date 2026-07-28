package com.flora.crypto.core.engine;

import com.flora.crypto.core.Agreement;
import com.flora.crypto.core.AsymmetricKeyParameter;
import com.flora.crypto.core.CipherParameters;

import com.flora.java.CheckUtil;

import javax.crypto.KeyAgreement;
import java.security.NoSuchAlgorithmException;

/**
 * 把 JDK 的 {@link KeyAgreement} 接入 {@link Agreement} 接口（ECDH / DH / X25519 等）。
 */
public final class JdkAgreement implements Agreement {

    private final String algorithm;
    private final KeyAgreement agreement;
    private java.security.Key privateKey;

    private JdkAgreement(String algorithm) {
        this.algorithm = algorithm;
        try {
            this.agreement = KeyAgreement.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("不支持的密钥协商算法: " + algorithm, e);
        }
    }

    public static JdkAgreement of(String algorithm) {
        CheckUtil.notEmpty(algorithm, "算法名不能为空");
        return new JdkAgreement(algorithm);
    }

    @Override
    public void init(CipherParameters params) {
        CheckUtil.notNull(params, "私钥参数不能为空");
        if (!(params instanceof AsymmetricKeyParameter)) {
            throw new IllegalArgumentException("密钥协商需要 AsymmetricKeyParameter（私钥）");
        }
        this.privateKey = ((AsymmetricKeyParameter) params).getKey();
    }

    @Override
    public byte[] calculateAgreement(CipherParameters pubKey) {
        CheckUtil.notNull(pubKey, "对方公钥参数不能为空");
        if (!(pubKey instanceof AsymmetricKeyParameter)) {
            throw new IllegalArgumentException("密钥协商需要 AsymmetricKeyParameter（公钥）");
        }
        try {
            agreement.init(privateKey);
            agreement.doPhase(((AsymmetricKeyParameter) pubKey).getKey(), true);
            return agreement.generateSecret();
        } catch (Exception e) {
            throw new IllegalStateException("密钥协商失败: " + algorithm, e);
        }
    }
}
