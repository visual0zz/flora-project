package com.flora.root.crypto.core.impl;

import com.flora.root.crypto.core.constant.AsymmetricKeyType;
import com.flora.root.crypto.core.interfaces.material.param.AsymmetricPrivateKeyParameter;
import com.flora.root.crypto.core.interfaces.material.param.AsymmetricPublicKeyParameter;
import com.flora.root.java.CheckUtil;

import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECKey;
import java.security.interfaces.EdECKey;
import java.security.interfaces.RSAKey;
import java.security.interfaces.XECKey;

/**
 * 非对称密钥参数的具体实现：持有 JDK {@link Key} 编码后的原始字节与 {@link AsymmetricKeyType} 种类。
 * <p>newcore 的 {@code AsymmetricPublicKeyParameter}/{@code AsymmetricPrivateKeyParameter} 仅暴露
 * 裸字节 + 种类枚举，本类将 JDK 的 {@code PublicKey}/{@code PrivateKey} 适配进来：{@link #fromKey}
 * 按密钥算法/参数名推断 {@link AsymmetricKeyType}，并把编码字节作为核心材料。</p>
 */
public final class AsymmetricKeyParameterImpl
        implements AsymmetricPublicKeyParameter, AsymmetricPrivateKeyParameter {

    private final byte[] keyBytes;
    private final AsymmetricKeyType keyType;
    private final Key jdkKey;

    public AsymmetricKeyParameterImpl(byte[] keyBytes, AsymmetricKeyType keyType) {
        CheckUtil.notNull(keyBytes, "密钥字节不能为空");
        CheckUtil.notNull(keyType, "密钥种类不能为空");
        this.keyBytes = keyBytes.clone();
        this.keyType = keyType;
        this.jdkKey = null;
    }

    /** 从 JDK 公钥适配（按算法/参数推断种类）。 */
    public static AsymmetricKeyParameterImpl fromPublic(PublicKey key) {
        CheckUtil.notNull(key, "公钥不能为空");
        return new AsymmetricKeyParameterImpl(key.getEncoded(), inferType(key), key);
    }

    /** 从 JDK 私钥适配（按算法/参数推断种类）。 */
    public static AsymmetricKeyParameterImpl fromPrivate(PrivateKey key) {
        CheckUtil.notNull(key, "私钥不能为空");
        return new AsymmetricKeyParameterImpl(key.getEncoded(), inferType(key), key);
    }

    private AsymmetricKeyParameterImpl(byte[] keyBytes, AsymmetricKeyType keyType, Key jdkKey) {
        CheckUtil.notNull(keyBytes, "密钥字节不能为空");
        CheckUtil.notNull(keyType, "密钥种类不能为空");
        this.keyBytes = keyBytes.clone();
        this.keyType = keyType;
        this.jdkKey = jdkKey;
    }

    /** @return 原始 JDK 密钥（若由 {@link #fromPublic}/{@link #fromPrivate} 构造），否则 {@code null}。 */
    public Key getJdkKey() {
        return jdkKey;
    }

    @Override
    public byte[] getPublicKey() {
        return keyBytes.clone();
    }

    @Override
    public byte[] getPrivateKey() {
        return keyBytes.clone();
    }

    @Override
    public AsymmetricKeyType getKeyKind() {
        return keyType;
    }

    private static AsymmetricKeyType inferType(Key key) {
        if (key instanceof RSAKey) {
            return AsymmetricKeyType.RSA;
        }
        if (key instanceof XECKey xec) {
            // X25519/X448 协商、Ed25519/Ed448 签名，底层同 Curve25519/448 族
            return AsymmetricKeyType.CURVE25519;
        }
        if (key instanceof EdECKey) {
            return AsymmetricKeyType.CURVE25519;
        }
        if (key instanceof ECKey) {
            return AsymmetricKeyType.EC;
        }
        if (key.getAlgorithm().equalsIgnoreCase("DH")
                || key.getAlgorithm().equalsIgnoreCase("DiffieHellman")) {
            return AsymmetricKeyType.DH;
        }
        if (key.getAlgorithm().toUpperCase().contains("SM2")) {
            return AsymmetricKeyType.SM2;
        }
        return AsymmetricKeyType.RAW;
    }
}
