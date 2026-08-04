package com.flora.crypto.core.mode;
import com.flora.tag.ThreadFragile;

import com.flora.crypto.core.interfaces.provider.BlockCipher;
import com.flora.crypto.core.interfaces.CipherParameters;
import com.flora.crypto.core.param.ParametersWithIV;
import com.flora.java.CheckUtil;

/**
 * OFB 模式（整块 OFB，纯 Java 链式）。
 * <p>包裹一个原始分组密码，把分组密码变成同步流密码。加密与解密使用完全相同的操作，
 * IV 作为初始状态。{@link #process(byte[])} 要求输入块对齐。</p>
 */
@ThreadFragile
public final class OFBBlockCipher implements BlockCipher {

    private final BlockCipher cipher;
    private final int blockSize;
    private final byte[] IV;
    private final byte[] cfbV;
    private final byte[] cfbOutV;

    public OFBBlockCipher(BlockCipher cipher) {
        this.cipher = cipher;
        this.blockSize = cipher.getBlockSize();
        this.IV = new byte[blockSize];
        this.cfbV = new byte[blockSize];
        this.cfbOutV = new byte[blockSize];
    }

    @Override
    public void init(boolean forEncryption, CipherParameters params) {
        CipherParameters param = params;
        byte[] iv = null;
        if (param instanceof ParametersWithIV) {
            iv = ((ParametersWithIV) param).getIV();
            param = ((ParametersWithIV) param).getParameters();
        }
        CheckUtil.notNull(param, "缺少密钥参数");
        cipher.init(true, param);
        if (iv != null) {
            System.arraycopy(iv, 0, IV, 0, blockSize);
        }
        System.arraycopy(IV, 0, cfbV, 0, blockSize);
    }

    @Override
    public String getAlgorithmName() {
        return cipher.getAlgorithmName() + "/OFB";
    }

    @Override
    public int getBlockSize() {
        return blockSize;
    }

    @Override
    public int processBlock(byte[] in, int inOff, byte[] out, int outOff) {
        cipher.processBlock(cfbV, 0, cfbOutV, 0);
        for (int i = 0; i < blockSize; i++) {
            out[outOff + i] = (byte) (in[inOff + i] ^ cfbOutV[i]);
        }
        System.arraycopy(cfbOutV, 0, cfbV, 0, blockSize);
        return blockSize;
    }

    @Override
    public byte[] process(byte[] data) {
        CheckUtil.notNull(data, "数据不能为空");
        if (data.length % blockSize != 0) {
            throw new IllegalStateException("OFB 模式要求输入块对齐");
        }
        byte[] out = new byte[data.length];
        for (int off = 0; off < data.length; off += blockSize) {
            processBlock(data, off, out, off);
        }
        return out;
    }
}
