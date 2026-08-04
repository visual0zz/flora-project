package com.flora.crypto.core.bridge;
import com.flora.tag.ThreadFragile;
import com.flora.crypto.core.interfaces.provider.Mac;
import com.flora.crypto.core.interfaces.CipherParameters;

import com.flora.java.CheckUtil;

import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * 把 JDK 自带的 {@link javax.crypto.Mac} 接入 {@link Mac} 接口。
 * <p>示例：{@code CryptoProvider.mac("HmacSHA256")}。</p>
 */
@ThreadFragile
public final class JdkMac implements Mac {

    private final String algorithm;
    private final javax.crypto.Mac mac;

    private JdkMac(String algorithm) {
        this.algorithm = algorithm;
        try {
            this.mac = javax.crypto.Mac.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("不支持的 MAC 算法: " + algorithm, e);
        }
    }

    public static JdkMac of(String algorithm) {
        CheckUtil.notEmpty(algorithm, "算法名不能为空");
        return new JdkMac(algorithm);
    }

    private static final java.util.Set<String> SUPPORTED = java.util.Set.of(
            "HmacMD5", "HmacSHA1", "HmacSHA224", "HmacSHA256", "HmacSHA384", "HmacSHA512");

    @Override
    public java.util.Set<String> supportedAlgorithms() {
        return SUPPORTED;
    }

    @Override
    public void init(CipherParameters params) {
        CheckUtil.notNull(params, "参数不能为空");
        try {
            mac.init(CipherSupport.secretKey(params, algorithm));
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("初始化 MAC 失败: " + algorithm, e);
        }
    }

    @Override
    public String getAlgorithmName() {
        return algorithm;
    }

    @Override
    public int getMacSize() {
        return mac.getMacLength();
    }

    @Override
    public void update(byte in) {
        mac.update(in);
    }

    @Override
    public void update(byte[] in, int inOff, int len) {
        mac.update(in, inOff, len);
    }

    @Override
    public int doFinal(byte[] out, int outOff) {
        byte[] result = mac.doFinal();
        System.arraycopy(result, 0, out, outOff, result.length);
        return result.length;
    }

    @Override
    public void reset() {
        mac.reset();
    }
}
