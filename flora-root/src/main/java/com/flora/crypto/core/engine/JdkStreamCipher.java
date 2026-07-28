package com.flora.crypto.core.engine;
import com.flora.tag.ThreadFragile;
import com.flora.crypto.core.StreamCipher;
import com.flora.crypto.core.CipherParameters;

import com.flora.java.CheckUtil;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * 把 JDK 自带的 {@link Cipher}（流密码模式）接入 {@link StreamCipher} 接口。
 * <p>示例：{@code CryptoProvider.streamCipher("RC4")}。</p>
 */
@ThreadFragile
public final class JdkStreamCipher implements StreamCipher {

    private final String transformation;
    private final Cipher cipher;

    private JdkStreamCipher(String transformation) {
        this.transformation = transformation;
        try {
            this.cipher = Cipher.getInstance(transformation);
        } catch (NoSuchAlgorithmException | javax.crypto.NoSuchPaddingException e) {
            throw new IllegalArgumentException("不支持的流密码变换: " + transformation, e);
        }
    }

    public static JdkStreamCipher of(String transformation) {
        CheckUtil.notEmpty(transformation, "变换字符串不能为空");
        return new JdkStreamCipher(transformation);
    }

    @Override
    public void init(boolean forEncryption, CipherParameters params) {
        CheckUtil.notNull(params, "参数不能为空");
        try {
            cipher.init(forEncryption ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE,
                    CipherSupport.secretKey(params, transformation));
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("初始化流密码失败: " + transformation, e);
        }
    }

    @Override
    public String getAlgorithmName() {
        return transformation;
    }

    @Override
    public byte processByte(byte in) {
        byte[] r = cipher.update(new byte[]{in});
        return r.length == 1 ? r[0] : 0;
    }

    @Override
    public int processBytes(byte[] in, int inOff, int len, byte[] out, int outOff) {
        byte[] r = cipher.update(in, inOff, len);
        int n = r == null ? 0 : r.length;
        if (n > 0) {
            System.arraycopy(r, 0, out, outOff, n);
        }
        return n;
    }
}
