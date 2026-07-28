package com.flora.crypto.core.engine;
import com.flora.tag.ThreadFragile;
import com.flora.crypto.core.BlockCipher;
import com.flora.crypto.core.CipherParameters;

import com.flora.java.CheckUtil;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * 把 JDK 自带的 {@link Cipher} 接入 {@link BlockCipher} 接口。
 * <p>模式与填充通过 transformation 字符串表达（如 {@code "AES/CBC/PKCS5Padding"}、
 * {@code "AES/GCM/NoPadding"}），作为适配器的配置项，符合「模式/填充外置于组合层」的思路。</p>
 *
 * <p>除实现 {@link BlockCipher} 的逐块 {@link #processBlock} 外，另提供便捷的
 * {@link #process(byte[])}：一次性处理整段数据（JDK 负责填充与 GCM 认证标签），
 * 是日常使用的主要入口。</p>
 */
@ThreadFragile
public final class JdkBlockCipher implements BlockCipher {

    private final String transformation;
    private final Cipher cipher;

    private JdkBlockCipher(String transformation) {
        this.transformation = transformation;
        try {
            this.cipher = Cipher.getInstance(transformation);
        } catch (NoSuchAlgorithmException | javax.crypto.NoSuchPaddingException e) {
            throw new IllegalArgumentException("不支持的分组密码变换: " + transformation, e);
        }
    }

    public static JdkBlockCipher of(String transformation) {
        CheckUtil.notEmpty(transformation, "变换字符串不能为空");
        return new JdkBlockCipher(transformation);
    }

    @Override
    public void init(boolean forEncryption, CipherParameters params) {
        CheckUtil.notNull(params, "参数不能为空");
        int mode = forEncryption ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE;
        try {
            if (transformation.contains("GCM")) {
                byte[] iv = CipherSupport.ivOf(params);
                if (iv == null) {
                    throw new IllegalArgumentException("GCM 模式需要 IV（ParametersWithIV）");
                }
                cipher.init(mode, CipherSupport.secretKey(params, transformation), new GCMParameterSpec(128, iv));
            } else if (CipherSupport.needsIv(transformation)) {
                byte[] iv = CipherSupport.ivOf(params);
                if (iv == null) {
                    throw new IllegalArgumentException("该模式需要 IV（ParametersWithIV）: " + transformation);
                }
                cipher.init(mode, CipherSupport.secretKey(params, transformation), new IvParameterSpec(iv));
            } else {
                cipher.init(mode, CipherSupport.secretKey(params, transformation));
            }
        } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
            throw new IllegalArgumentException("初始化分组密码失败: " + transformation, e);
        }
    }

    @Override
    public String getAlgorithmName() {
        return transformation;
    }

    @Override
    public int getBlockSize() {
        return cipher.getBlockSize();
    }

    @Override
    public int processBlock(byte[] in, int inOff, byte[] out, int outOff) {
        try {
            int n = cipher.update(in, inOff, getBlockSize(), out, outOff);
            if (n == 0) {
                byte[] r = cipher.doFinal(in, inOff, getBlockSize());
                System.arraycopy(r, 0, out, outOff, r.length);
                return r.length;
            }
            return n;
        } catch (Exception e) {
            throw new IllegalStateException("处理块失败: " + transformation, e);
        }
    }

    /**
     * 便捷入口：一次性处理整段数据（含填充 / GCM 认证标签）。
     *
     * @param data 明文或密文
     * @return 密文或明文
     */
    public byte[] process(byte[] data) {
        CheckUtil.notNull(data, "数据不能为空");
        try {
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("加解密失败: " + transformation, e);
        }
    }
}
