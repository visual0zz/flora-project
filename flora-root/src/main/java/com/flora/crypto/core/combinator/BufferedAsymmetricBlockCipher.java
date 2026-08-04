package com.flora.crypto.core.combinator;
import com.flora.crypto.core.interfaces.provider.AsymmetricBlockCipher;
import com.flora.crypto.core.interfaces.provider.AsymmetricCipher;
import com.flora.crypto.core.interfaces.CipherParameters;
import com.flora.tag.ThreadFragile;

import com.flora.java.CheckUtil;

import java.io.ByteArrayOutputStream;

/**
 * 非对称分组密码的流式缓冲装饰器（Bouncy Castle 风格）。
 * <p>包裹任意 {@link AsymmetricBlockCipher}，把「整块处理」适配成 {@link AsymmetricCipher} 的
 * 「逐字节/逐段流式」接口：输入累积到整块大小即调用底层处理，余下不足一块的部分留待后续
 * （非对称块密码本就要求整块输入）。</p>
 */
@ThreadFragile
public final class BufferedAsymmetricBlockCipher implements AsymmetricCipher {

    private final AsymmetricBlockCipher cipher;
    private int inBlockSize;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private boolean forEncryption;

    public BufferedAsymmetricBlockCipher(AsymmetricBlockCipher cipher) {
        CheckUtil.notNull(cipher, "底层非对称分组密码不能为空");
        this.cipher = cipher;
        this.inBlockSize = cipher.getInputBlockSize();
    }

    @Override
    public void init(boolean forEncryption, CipherParameters params) {
        this.forEncryption = forEncryption;
        cipher.init(forEncryption, params);
        // 加密方向按输入块大小缓冲；解密方向按输出块大小（即密文块大小）缓冲
        this.inBlockSize = forEncryption ? cipher.getInputBlockSize() : cipher.getOutputBlockSize();
        buffer.reset();
    }

    @Override
    public String getAlgorithmName() {
        return cipher.getAlgorithmName();
    }

    @Override
    public int processByte(byte in, byte[] out, int outOff) {
        buffer.write(in);
        return processFullBlocks(out, outOff);
    }

    @Override
    public int processBytes(byte[] in, int inOff, int len, byte[] out, int outOff) {
        buffer.write(in, inOff, len);
        return processFullBlocks(out, outOff);
    }

    private int processFullBlocks(byte[] out, int outOff) {
        int written = 0;
        byte[] data = buffer.toByteArray();
        int fullBlocks = data.length / inBlockSize;
        for (int i = 0; i < fullBlocks; i++) {
            byte[] block = cipher.processBlock(data, i * inBlockSize, inBlockSize);
            System.arraycopy(block, 0, out, outOff + written, block.length);
            written += block.length;
        }
        // 保留不足一块的尾部
        int consumed = fullBlocks * inBlockSize;
        buffer.reset();
        if (consumed < data.length) {
            buffer.write(data, consumed, data.length - consumed);
        }
        return written;
    }

    @Override
    public void reset() {
        buffer.reset();
    }
}
