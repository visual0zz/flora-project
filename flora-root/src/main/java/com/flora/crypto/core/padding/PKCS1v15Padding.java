package com.flora.crypto.core.padding;

import com.flora.crypto.core.AsymmetricPadding;
import com.flora.java.CheckUtil;

import java.security.SecureRandom;

/**
 * EME-PKCS1-v1.5 填充（RFC 8017 §7.2）。
 * <p>加密块结构：{@code 0x00 ‖ 0x02 ‖ PS ‖ 0x00 ‖ M}，PS 为至少 8 字节的随机非零字节。
 * 最小开销 11 字节（{@code 0x00 0x02 0x00} + 8 字节 PS）。</p>
 */
public final class PKCS1v15Padding implements AsymmetricPadding {

    private final SecureRandom random;

    public PKCS1v15Padding() {
        this(new SecureRandom());
    }

    public PKCS1v15Padding(SecureRandom random) {
        CheckUtil.notNull(random, "随机源不能为空");
        this.random = random;
    }

    @Override
    public String getPaddingName() {
        return "PKCS1v15";
    }

    @Override
    public int getInputBlockSize(int keyBytes) {
        return keyBytes - 11;
    }

    @Override
    public byte[] pad(byte[] in, int inOff, int inLen, int keyBytes) {
        CheckUtil.notNull(in, "输入不能为空");
        if (inLen > keyBytes - 11) {
            throw new IllegalArgumentException("明文过长: " + inLen + " > " + (keyBytes - 11));
        }
        byte[] em = new byte[keyBytes];
        em[0] = 0x00;
        em[1] = 0x02;
        int psLen = keyBytes - 3 - inLen;
        byte[] ps = new byte[psLen];
        random.nextBytes(ps);
        for (int i = 0; i < psLen; i++) {
            while (ps[i] == 0) {
                ps[i] = (byte) random.nextInt();
            }
        }
        System.arraycopy(ps, 0, em, 2, psLen);
        em[keyBytes - inLen - 1] = 0x00;
        System.arraycopy(in, inOff, em, keyBytes - inLen, inLen);
        return em;
    }

    @Override
    public byte[] unpad(byte[] in) throws IllegalArgumentException {
        CheckUtil.notNull(in, "输入不能为空");
        if (in.length < 11) {
            throw new IllegalArgumentException("PKCS1v15 块过短");
        }
        if (in[0] != 0x00 || in[1] != 0x02) {
            throw new IllegalArgumentException("PKCS1v15 块头非法");
        }
        int sep = -1;
        for (int i = 2; i < in.length; i++) {
            if (in[i] == 0x00) {
                sep = i;
                break;
            }
        }
        if (sep < 0) {
            throw new IllegalArgumentException("PKCS1v15 缺少分隔符");
        }
        int psLen = sep - 2;
        if (psLen < 8) {
            throw new IllegalArgumentException("PKCS1v15 填充过短");
        }
        for (int i = 2; i < sep; i++) {
            if (in[i] == 0x00) {
                throw new IllegalArgumentException("PKCS1v15 PS 含零字节");
            }
        }
        byte[] m = new byte[in.length - sep - 1];
        System.arraycopy(in, sep + 1, m, 0, m.length);
        return m;
    }
}
