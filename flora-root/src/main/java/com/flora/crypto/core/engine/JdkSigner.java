package com.flora.crypto.core.engine;
import com.flora.tag.ThreadFragile;
import com.flora.crypto.core.Signer;
import com.flora.crypto.core.CipherParameters;

import com.flora.java.CheckUtil;

import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;

/**
 * 把 JDK 自带的 {@link Signature} 接入 {@link Signer} 接口。
 * <p>示例：{@code CryptoProvider.signer("SHA256withRSA")}。</p>
 */
@ThreadFragile
public final class JdkSigner implements Signer {

    private final String algorithm;
    private final Signature signature;

    private JdkSigner(String algorithm) {
        this.algorithm = algorithm;
        try {
            this.signature = Signature.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("不支持的签名算法: " + algorithm, e);
        }
    }

    public static JdkSigner of(String algorithm) {
        CheckUtil.notEmpty(algorithm, "算法名不能为空");
        return new JdkSigner(algorithm);
    }

    @Override
    public void init(boolean forSigning, CipherParameters params) {
        CheckUtil.notNull(params, "参数不能为空");
        Key key = CipherSupport.asymmetricKeyParameter(params).getKey();
        try {
            if (forSigning) {
                signature.initSign((PrivateKey) key);
            } else {
                signature.initVerify((PublicKey) key);
            }
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("初始化签名器失败: " + algorithm, e);
        }
    }

    @Override
    public String getAlgorithmName() {
        return algorithm;
    }

    @Override
    public void update(byte in) {
        try {
            signature.update(in);
        } catch (SignatureException e) {
            throw new IllegalStateException("更新签名数据失败: " + algorithm, e);
        }
    }

    @Override
    public void update(byte[] in, int inOff, int len) {
        try {
            signature.update(in, inOff, len);
        } catch (SignatureException e) {
            throw new IllegalStateException("更新签名数据失败: " + algorithm, e);
        }
    }

    @Override
    public byte[] generateSignature() {
        try {
            return signature.sign();
        } catch (SignatureException e) {
            throw new IllegalStateException("签名失败: " + algorithm, e);
        }
    }

    @Override
    public boolean verifySignature(byte[] sig) {
        try {
            return signature.verify(sig);
        } catch (SignatureException e) {
            throw new IllegalStateException("验签失败: " + algorithm, e);
        }
    }
}
