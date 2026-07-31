package com.flora.crypto.core;
import com.flora.crypto.core.interfaces.provider.BlockCipher;
import com.flora.crypto.core.interfaces.provider.BlockCipherPadding;
import com.flora.crypto.core.interfaces.CipherParameters;
import com.flora.tag.ThreadFragile;

import com.flora.crypto.core.padding.PKCS7Padding;
import com.flora.java.CheckUtil;

import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;

/**
 * 带填充的分组密码缓冲装饰器（Bouncy Castle 风格）。
 * <p>组合一个 {@link BlockCipher} 与一个 {@link BlockCipherPadding}，使「逐块」引擎也能直接处理
 * 任意长度数据：加密时在末块加填充，解密时从末块去除填充。默认使用 PKCS7 填充。</p>
 */
@ThreadFragile
public final class PaddedBufferedBlockCipher {

    private final BlockCipher cipher;
    private final BlockCipherPadding padding;
    private final int blockSize;
    private boolean forEncryption;

    public PaddedBufferedBlockCipher(BlockCipher cipher, BlockCipherPadding padding) {
        CheckUtil.notNull(cipher, "底层分组密码不能为空");
        CheckUtil.notNull(padding, "填充策略不能为空");
        this.cipher = cipher;
        this.padding = padding;
        this.blockSize = cipher.getBlockSize();
    }

    public PaddedBufferedBlockCipher(BlockCipher cipher) {
        this(cipher, new PKCS7Padding());
    }

    public void init(boolean forEncryption, CipherParameters params) {
        this.forEncryption = forEncryption;
        cipher.init(forEncryption, params);
        padding.init(new SecureRandom());
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
        int end = off + len;
        while (pos + blockSize <= end) {
            byte[] block = new byte[blockSize];
            cipher.processBlock(data, pos, block, 0);
            out.write(block, 0, blockSize);
            pos += blockSize;
        }

        if (forEncryption) {
            byte[] last = new byte[blockSize];
            int rem = end - pos;
            System.arraycopy(data, pos, last, 0, rem);
            padding.addPadding(last, rem);
            byte[] block = new byte[blockSize];
            cipher.processBlock(last, 0, block, 0);
            out.write(block, 0, blockSize);
        } else {
            if (pos != end) {
                throw new IllegalStateException("密文长度不是块大小的整数倍，无法去填充");
            }
            byte[] full = out.toByteArray();
            int lastStart = full.length - blockSize;
            byte[] lastBlock = new byte[blockSize];
            System.arraycopy(full, lastStart, lastBlock, 0, blockSize);
            int pad = padding.padCount(lastBlock);
            out.reset();
            out.write(full, 0, lastStart);
            out.write(lastBlock, 0, blockSize - pad);
        }
        return out.toByteArray();
    }
}
