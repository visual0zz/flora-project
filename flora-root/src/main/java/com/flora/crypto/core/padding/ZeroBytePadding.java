package com.flora.crypto.core.padding;

import com.flora.crypto.core.BlockCipherPadding;

import java.security.SecureRandom;

/**
 * 零字节填充（末尾补 {@code 0x00} 至块对齐）。
 * <p>注意：零填充无法无歧义地还原原始长度（末尾本来就是 0 的情况），
 * 仅适合已知明文长度或定长字段的场景。</p>
 */
public final class ZeroBytePadding implements BlockCipherPadding {

    @Override
    public void init(SecureRandom random) {
        // 不需要随机数
    }

    @Override
    public String getPaddingName() {
        return "ZeroByte";
    }

    @Override
    public int addPadding(byte[] in, int inOff) {
        int count = 0;
        while (inOff < in.length) {
            in[inOff++] = 0;
            count++;
        }
        return count;
    }

    @Override
    public int padCount(byte[] in) {
        int count = 0;
        int i = in.length - 1;
        while (i >= 0 && in[i] == 0) {
            i--;
            count++;
        }
        return count;
    }

    @Override
    public int getPaddingSize() {
        return 0; // 变长填充
    }
}
