package com.flora.crypto.core.padding;

import com.flora.crypto.core.BlockCipherPadding;

import java.security.SecureRandom;

/**
 * PKCS#7 填充（PKCS#5 为其块大小 8 的特例）。
 * <p>在 {@code in[inOff]} 起填充至块对齐，每个填充字节的值等于填充长度。</p>
 */
public final class PKCS7Padding implements BlockCipherPadding {

    @Override
    public void init(SecureRandom random) {
        // PKCS7 不需要随机数
    }

    @Override
    public String getPaddingName() {
        return "PKCS7";
    }

    @Override
    public int addPadding(byte[] in, int inOff) {
        int count = in.length - inOff;
        byte code = (byte) count;
        while (inOff < in.length) {
            in[inOff++] = code;
        }
        return count;
    }

    @Override
    public int padCount(byte[] in) {
        int count = in[in.length - 1] & 0xFF;
        if (count < 1 || count > in.length) {
            throw new IllegalStateException("PKCS7 填充非法: 长度=" + count);
        }
        for (int i = in.length - count; i < in.length - 1; i++) {
            if ((in[i] & 0xFF) != count) {
                throw new IllegalStateException("PKCS7 填充非法");
            }
        }
        return count;
    }

    @Override
    public int getPaddingSize() {
        return 0; // 变长填充
    }
}
