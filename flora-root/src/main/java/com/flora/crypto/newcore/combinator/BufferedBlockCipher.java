package com.flora.crypto.newcore.combinator;

import com.flora.crypto.newcore.interfaces.algorithm.BlockCipher;
import com.flora.crypto.newcore.interfaces.material.param.CipherParameter;
import com.flora.java.CheckUtil;
import com.flora.tag.ThreadFragile;

import java.io.ByteArrayOutputStream;

/**
 * 分组密码缓冲装饰器。
 * <p>包装任意 {@link BlockCipher}，缓冲任意长度输入并成块吐出，使底层的「逐块」接口
 * 也能直接处理整段数据。这是组合模式的典型示范：模式、填充等变化点通过
 * 可叠加的包装器表达，而非放进统一的算法接口。</p>
 * <p>本装饰器只做缓冲，不实现任何加密逻辑。对于 {@code NoPadding} 引擎，要求输入长度为
 * 块大小的整数倍；非对齐数据请改用带填充的 {@link PaddedBufferedBlockCipher}。</p>
 */
@ThreadFragile
public final class BufferedBlockCipher {

    private final BlockCipher cipher;
    private final int blockSize;
    private final byte[] buffer;
    private int bufOff;

    public BufferedBlockCipher(BlockCipher cipher) {
        CheckUtil.notNull(cipher, "底层分组密码不能为空");
        this.cipher = cipher;
        this.blockSize = cipher.getBlockSize();
        this.buffer = new byte[blockSize];
        this.bufOff = 0;
    }

    public void init(boolean forEncryption, CipherParameter params) {
        cipher.init(forEncryption, params);
        bufOff = 0;
    }

    public String getAlgorithmName() {
        return cipher.getAlgorithmName();
    }

    public int getBlockSize() {
        return blockSize;
    }

    public byte[] process(byte[] data) {
        CheckUtil.notNull(data, "数据不能为空");
        return process(data, 0, data.length);
    }

    public byte[] process(byte[] data, int off, int len) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int pos = off;
        int remaining = len;
        while (remaining > 0) {
            int take = Math.min(remaining, blockSize - bufOff);
            System.arraycopy(data, pos, buffer, bufOff, take);
            bufOff += take;
            pos += take;
            remaining -= take;
            if (bufOff == blockSize) {
                byte[] block = new byte[blockSize];
                cipher.processBlock(buffer, 0, block, 0);
                out.write(block, 0, blockSize);
                bufOff = 0;
            }
        }
        return out.toByteArray();
    }

    /**
     * 冲刷剩余缓冲。若缓冲中留有不足一块的数据，说明输入未块对齐（NoPadding 引擎不允许）。
     *
     * @return 末尾完整块的处理结果；缓冲为空时返回空数组
     */
    public byte[] doFinal() {
        if (bufOff == 0) {
            return new byte[0];
        }
        if (bufOff != blockSize) {
            throw new IllegalStateException(
                    "存在不完整的块（" + bufOff + " 字节）；NoPadding 引擎要求数据块对齐，或使用带填充的包装器");
        }
        byte[] block = new byte[blockSize];
        cipher.processBlock(buffer, 0, block, 0);
        bufOff = 0;
        return block;
    }
}
