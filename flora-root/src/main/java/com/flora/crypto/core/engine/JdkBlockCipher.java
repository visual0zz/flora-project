package com.flora.crypto.core.engine;
import com.flora.tag.ThreadFragile;
import com.flora.crypto.core.interfaces.provider.BlockCipher;
import com.flora.crypto.core.interfaces.CipherParameters;
import com.flora.crypto.core.KeyParameter;

import com.flora.java.CheckUtil;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * JDK 分组密码原语接入 {@link BlockCipher} 接口（仅裸块引擎，无组合逻辑）。
 * <p>只接受裸算法名（如 {@code "AES"}），内部固定使用 {@code "算法/ECB/NoPadding"}
 * 变换——ECB 无链接逻辑、NoPadding 无填充，语义上等价于"单块分组密码引擎"。
 * 模式（CBC/CFB/OFB/CTR/GCM）与填充（PKCS7/OAEP 等）由组合层自研类编排，不走 JDK 组合结构。</p>
 */
@ThreadFragile
public final class JdkBlockCipher implements BlockCipher {

    private final String algorithm;
    private final String transformation;
    private final Cipher cipher;

    private JdkBlockCipher(String algorithm) {
        this.algorithm = algorithm;
        this.transformation = algorithm + "/ECB/NoPadding";
        try {
            this.cipher = Cipher.getInstance(transformation);
        } catch (NoSuchAlgorithmException | javax.crypto.NoSuchPaddingException e) {
            throw new IllegalArgumentException("不支持的分组密码算法: " + algorithm, e);
        }
    }

    public static JdkBlockCipher of(String algorithm) {
        CheckUtil.notEmpty(algorithm, "算法名不能为空");
        if (algorithm.indexOf('/') >= 0) {
            throw new IllegalArgumentException("只接受裸算法名（如 \"AES\"），组合变换由自研组合层编排: " + algorithm);
        }
        return new JdkBlockCipher(algorithm);
    }

    private static final java.util.Set<String> SUPPORTED = java.util.Set.of(
            "AES", "DES", "DESede", "Blowfish", "RC2");

    @Override
    public java.util.Set<String> supportedAlgorithms() {
        return SUPPORTED;
    }

    @Override
    public void init(boolean forEncryption, CipherParameters params) {
        CheckUtil.notNull(params, "参数不能为空");
        KeyParameter keyParam = CipherSupport.keyParameter(params);
        try {
            cipher.init(forEncryption ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE,
                    new SecretKeySpec(keyParam.getKey(), algorithm));
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("初始化分组密码失败: " + transformation, e);
        }
    }

    @Override
    public String getAlgorithmName() {
        return algorithm;
    }

    @Override
    public int getBlockSize() {
        return cipher.getBlockSize();
    }

    @Override
    public int processBlock(byte[] in, int inOff, byte[] out, int outOff) {
        try {
            return cipher.update(in, inOff, getBlockSize(), out, outOff);
        } catch (Exception e) {
            throw new IllegalStateException("处理块失败: " + transformation, e);
        }
    }

    @Override
    public byte[] process(byte[] data) {
        CheckUtil.notNull(data, "数据不能为空");
        if (data.length % getBlockSize() != 0) {
            throw new IllegalStateException("裸块引擎要求输入为块大小整数倍");
        }
        byte[] out = new byte[data.length];
        for (int off = 0; off < data.length; off += getBlockSize()) {
            processBlock(data, off, out, off);
        }
        return out;
    }
}
