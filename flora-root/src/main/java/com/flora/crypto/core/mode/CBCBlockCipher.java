package com.flora.crypto.core.mode;
import com.flora.tag.ThreadFragile;

import com.flora.crypto.core.interfaces.provider.BlockCipher;
import com.flora.crypto.core.interfaces.CipherParameters;
import com.flora.crypto.core.param.ParametersWithIV;
import com.flora.java.CheckUtil;

/**
 * CBC 模式（纯 Java 链式）。
 * <p>包裹一个原始分组密码（如 {@code AES/ECB/NoPadding}），通过 IV 实现密文块链接，
 * 不依赖 JDK 的 {@code "AES/CBC/..."} 变换字符串——这是「对象组合」优于「字符串变换」的示范。</p>
 * <p>注意：本类不做填充，{@link #process(byte[])} 要求输入为块大小整数倍；
 * 需要填充请改用 {@code PaddedBufferedBlockCipher} 或在 JDK 适配器里处理。</p>
 */
@ThreadFragile
public final class CBCBlockCipher implements BlockCipher {

    private final BlockCipher cipher;
    private final int blockSize;
    private final byte[] IV;
    private final byte[] cbcV;
    private final byte[] cbcNextV;
    private boolean encrypting;

    public CBCBlockCipher(BlockCipher cipher) {
        this.cipher = cipher;
        this.blockSize = cipher.getBlockSize();
        this.IV = new byte[blockSize];
        this.cbcV = new byte[blockSize];
        this.cbcNextV = new byte[blockSize];
    }

    @Override
    public void init(boolean forEncryption, CipherParameters params) {
        this.encrypting = forEncryption;
        CipherParameters param = params;
        byte[] iv = null;
        if (param instanceof ParametersWithIV) {
            iv = ((ParametersWithIV) param).getIV();
            param = ((ParametersWithIV) param).getParameters();
        }
        CheckUtil.notNull(param, "缺少密钥参数");
        cipher.init(forEncryption, param);
        if (iv != null) {
            System.arraycopy(iv, 0, IV, 0, blockSize);
        }
        System.arraycopy(IV, 0, cbcV, 0, blockSize);
        System.arraycopy(IV, 0, cbcNextV, 0, blockSize);
    }

    @Override
    public String getAlgorithmName() {
        return cipher.getAlgorithmName() + "/CBC";
    }

    @Override
    public int getBlockSize() {
        return blockSize;
    }

    @Override
    public int processBlock(byte[] in, int inOff, byte[] out, int outOff) {
        return encrypting ? encryptBlock(in, inOff, out, outOff)
                : decryptBlock(in, inOff, out, outOff);
    }

    private int encryptBlock(byte[] in, int inOff, byte[] out, int outOff) {
        for (int i = 0; i < blockSize; i++) {
            cbcV[i] ^= in[inOff + i];
        }
        cipher.processBlock(cbcV, 0, out, outOff);
        System.arraycopy(out, outOff, cbcV, 0, blockSize);
        return blockSize;
    }

    private int decryptBlock(byte[] in, int inOff, byte[] out, int outOff) {
        System.arraycopy(in, inOff, cbcNextV, 0, blockSize);
        cipher.processBlock(in, inOff, out, outOff);
        for (int i = 0; i < blockSize; i++) {
            out[outOff + i] ^= cbcV[i];
        }
        System.arraycopy(cbcNextV, 0, cbcV, 0, blockSize);
        return blockSize;
    }

    @Override
    public byte[] process(byte[] data) {
        CheckUtil.notNull(data, "数据不能为空");
        if (data.length % blockSize != 0) {
            throw new IllegalStateException("CBC 无填充模式要求输入块对齐，或使用 PaddedBufferedBlockCipher");
        }
        byte[] out = new byte[data.length];
        for (int off = 0; off < data.length; off += blockSize) {
            processBlock(data, off, out, off);
        }
        return out;
    }
}
