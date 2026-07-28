package com.flora.crypto.core.engine;

import com.flora.crypto.core.AEADBlockCipher;
import com.flora.crypto.core.AsymmetricKeyParameter;
import com.flora.crypto.core.CipherParameters;
import com.flora.crypto.core.KeyParameter;
import com.flora.crypto.core.ParametersWithIV;

import com.flora.java.CheckUtil;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.Key;

/**
 * 把 JDK 的 AEAD {@link Cipher}（GCM / CCM / ChaCha20-Poly1305）接入 {@link AEADBlockCipher} 接口，
 * 暴露关联数据（AAD）与认证标签（MAC）的一等处理能力。
 * <p>注意：JDK 把 GCM 标签直接附在 {@code doFinal} 密文末尾，因此 {@link #getMac()} 在加密后返回
 * 密文末 16 字节（GCM 标签）；解密后调用通常无独立标签可读，返回空数组。</p>
 */
public final class JdkAeadBlockCipher implements AEADBlockCipher {

    private final String transformation;
    private final String baseAlgorithm;
    private final Cipher cipher;
    private final int tagLen;
    private boolean forEncryption;
    private byte[] lastOutput;

    private JdkAeadBlockCipher(String transformation) {
        this.transformation = transformation;
        this.baseAlgorithm = CipherSupport.baseCipher(transformation);
        this.tagLen = transformation.contains("GCM") ? 16 : 0;
        try {
            this.cipher = Cipher.getInstance(transformation);
        } catch (Exception e) {
            throw new IllegalArgumentException("不支持的 AEAD 变换: " + transformation, e);
        }
    }

    public static JdkAeadBlockCipher of(String transformation) {
        CheckUtil.notEmpty(transformation, "变换字符串不能为空");
        return new JdkAeadBlockCipher(transformation);
    }

    @Override
    public void init(boolean forEncryption, CipherParameters params) {
        CheckUtil.notNull(params, "参数不能为空");
        this.forEncryption = forEncryption;
        CipherParameters p = params;
        byte[] iv = null;
        if (p instanceof ParametersWithIV) {
            iv = ((ParametersWithIV) p).getIV();
            p = ((ParametersWithIV) p).getParameters();
        }
        Key key = keyOf(p);
        try {
            if (transformation.contains("GCM")) {
                if (iv == null) {
                    throw new IllegalArgumentException("GCM 模式需要 IV（ParametersWithIV）");
                }
                cipher.init(forEncryption ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE,
                        key, new GCMParameterSpec(128, iv));
            } else {
                if (iv == null) {
                    throw new IllegalArgumentException("该 AEAD 模式需要 IV（ParametersWithIV）: " + transformation);
                }
                cipher.init(forEncryption ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE,
                        key, new IvParameterSpec(iv));
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("初始化 AEAD 失败: " + transformation, e);
        }
    }

    @Override
    public String getAlgorithmName() {
        return transformation;
    }

    @Override
    public int getOutputSize(int len) {
        return cipher.getOutputSize(len);
    }

    @Override
    public int getUpdateOutputSize(int len) {
        return cipher.getOutputSize(len);
    }

    @Override
    public void processAADByte(byte in) {
        cipher.updateAAD(new byte[]{in});
    }

    @Override
    public void processAADBytes(byte[] in, int inOff, int len) {
        cipher.updateAAD(in, inOff, len);
    }

    @Override
    public int processByte(byte in, byte[] out, int outOff) {
        byte[] r = cipher.update(new byte[]{in});
        if (r == null || r.length == 0) {
            return 0;
        }
        System.arraycopy(r, 0, out, outOff, r.length);
        return r.length;
    }

    @Override
    public int processBytes(byte[] in, int inOff, int len, byte[] out, int outOff) {
        byte[] r = cipher.update(in, inOff, len);
        if (r == null || r.length == 0) {
            return 0;
        }
        System.arraycopy(r, 0, out, outOff, r.length);
        return r.length;
    }

    @Override
    public int doFinal(byte[] out, int outOff) {
        try {
            byte[] r = cipher.doFinal();
            this.lastOutput = r;
            if (r == null) {
                return 0;
            }
            System.arraycopy(r, 0, out, outOff, r.length);
            return r.length;
        } catch (Exception e) {
            throw new IllegalStateException("AEAD 处理失败: " + transformation, e);
        }
    }

    @Override
    public byte[] getMac() {
        if (!forEncryption || lastOutput == null || tagLen == 0) {
            return new byte[0];
        }
        byte[] mac = new byte[tagLen];
        System.arraycopy(lastOutput, lastOutput.length - tagLen, mac, 0, tagLen);
        return mac;
    }

    private Key keyOf(CipherParameters params) {
        if (params instanceof KeyParameter) {
            return new SecretKeySpec(((KeyParameter) params).getKey(), baseAlgorithm);
        }
        if (params instanceof AsymmetricKeyParameter) {
            return ((AsymmetricKeyParameter) params).getKey();
        }
        throw new IllegalArgumentException("需要 KeyParameter 或 AsymmetricKeyParameter");
    }
}
