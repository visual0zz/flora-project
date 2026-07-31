package com.flora.crypto.core.mode;
import com.flora.tag.ThreadFragile;

import com.flora.crypto.core.interfaces.provider.BlockCipher;
import com.flora.crypto.core.interfaces.CipherParameters;
import com.flora.crypto.core.ParametersWithIV;
import com.flora.java.CheckUtil;

/**
 * SIC 模式（即 CTR 计数器模式，纯 Java 链式，Bouncy Castle 风格）。
 * <p>包裹一个原始分组密码，以 IV 为初始计数器，逐块加密计数器并异或明文。
 * 加密与解密操作相同。{@link #process(byte[])} 要求输入块对齐。</p>
 */
@ThreadFragile
public final class SICBlockCipher implements BlockCipher {

    private final BlockCipher cipher;
    private final int blockSize;
    private final byte[] IV;
    private final byte[] counter;
    private final byte[] cfbOutV;

    public SICBlockCipher(BlockCipher cipher) {
        this.cipher = cipher;
        this.blockSize = cipher.getBlockSize();
        this.IV = new byte[blockSize];
        this.counter = new byte[blockSize];
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
        System.arraycopy(IV, 0, counter, 0, blockSize);
    }

    @Override
    public String getAlgorithmName() {
        return cipher.getAlgorithmName() + "/CTR";
    }

    @Override
    public int getBlockSize() {
        return blockSize;
    }

    @Override
    public int processBlock(byte[] in, int inOff, byte[] out, int outOff) {
        cipher.processBlock(counter, 0, cfbOutV, 0);
        for (int i = 0; i < blockSize; i++) {
            out[outOff + i] = (byte) (in[inOff + i] ^ cfbOutV[i]);
        }
        incrementCounter();
        return blockSize;
    }

    private void incrementCounter() {
        for (int i = blockSize - 1; i >= 0; i--) {
            if (++counter[i] != 0) {
                break;
            }
        }
    }

    @Override
    public byte[] process(byte[] data) {
        CheckUtil.notNull(data, "数据不能为空");
        if (data.length % blockSize != 0) {
            throw new IllegalStateException("CTR 模式要求输入块对齐");
        }
        byte[] out = new byte[data.length];
        for (int off = 0; off < data.length; off += blockSize) {
            processBlock(data, off, out, off);
        }
        return out;
    }
}
