package com.flora.crypto.core.padding;

import com.flora.crypto.core.BlockCipherPadding;

import java.security.SecureRandom;

/**
 * ISO 7816-4 填充（先填一个 {@code 0x80}，其后补 {@code 0x00} 至块对齐）。
 */
public final class ISO7816d4Padding implements BlockCipherPadding {

    @Override
    public void init(SecureRandom random) {
        // 不需要随机数
    }

    @Override
    public String getPaddingName() {
        return "ISO7816-4";
    }

    @Override
    public int addPadding(byte[] in, int inOff) {
        in[inOff] = (byte) 0x80;
        int count = 1;
        for (int i = inOff + 1; i < in.length; i++) {
            in[i] = 0;
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
        if (i < 0 || in[i] != (byte) 0x80) {
            throw new IllegalStateException("ISO7816-4 填充非法");
        }
        return count + 1;
    }

    @Override
    public int getPaddingSize() {
        return 0; // 变长填充
    }
}
