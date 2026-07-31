package com.flora.crypto.core.padding;

import com.flora.crypto.core.Digest;
import com.flora.java.CheckUtil;

/**
 * MGF1 掩码生成函数（RFC 8017 §B.2.1）。
 * <p>MGF1(seed, maskLen) = T0‖T1‖...‖T(n-1)，Ti = H(seed ‖ I2OSP(i, 4))，
 * 循环直至累计长度 ≥ maskLen，截取前 maskLen 字节。</p>
 */
public final class Mgf1Generator {

    private final Digest digest;

    public Mgf1Generator(Digest digest) {
        CheckUtil.notNull(digest, "摘要不能为空");
        this.digest = digest;
    }

    /**
     * 生成掩码。
     *
     * @param seed    种子
     * @param seedOff 种子偏移
     * @param seedLen 种子长度
     * @param out     输出缓冲区
     * @param outOff  输出偏移
     * @param maskLen 掩码长度（字节）
     */
    public void generateMask(byte[] seed, int seedOff, int seedLen,
                             byte[] out, int outOff, int maskLen) {
        CheckUtil.notNull(seed, "种子不能为空");
        CheckUtil.notNull(out, "输出不能为空");
        if (maskLen > (long) digest.getDigestSize() * 0x100000000L) {
            throw new IllegalArgumentException("maskLen 超出 MGF1 上限");
        }
        byte[] counter = new byte[4];
        byte[] hash = new byte[digest.getDigestSize()];
        int pos = outOff;
        int end = outOff + maskLen;
        int i = 0;
        while (pos < end) {
            counter[0] = (byte) (i >>> 24);
            counter[1] = (byte) (i >>> 16);
            counter[2] = (byte) (i >>> 8);
            counter[3] = (byte) i;
            digest.reset();
            digest.update(seed, seedOff, seedLen);
            digest.update(counter, 0, 4);
            digest.doFinal(hash, 0);
            int chunk = Math.min(hash.length, end - pos);
            System.arraycopy(hash, 0, out, pos, chunk);
            pos += chunk;
            i++;
        }
    }
}
