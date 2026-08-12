package com.flora.root.crypto.core.impl;

import com.flora.root.common.register.AlgorithmComponent;
import com.flora.root.common.register.AlgorithmFactory;
import com.flora.root.common.register.AlgorithmFactoryRegister;
import com.flora.root.crypto.core.CryptoAlgorithmFactoryRegister;
import com.flora.root.crypto.core.interfaces.algorithm.Digest;

import java.util.Arrays;
import java.util.Set;

/**
 * RIPEMD-160 摘要（RFC 2286，MD4 风格双通道结构）。
 * <p>5 轮 × 16 步共 80 步，左右双通道并行，步末组合两个通道状态。
 * 输出 20 字节，内部块 64 字节。为 JDK 缺失的摘要算法提供自研实现。</p>
 */
public final class Ripemd160Digest implements Digest {

    // 左通道消息字排列
    private static final int[] RL = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
            7, 4, 13, 1, 10, 6, 15, 3, 12, 0, 9, 5, 2, 14, 11, 8, 3, 10, 14, 4, 9, 15, 8, 1, 2,
            7, 0, 6, 13, 11, 5, 12, 1, 9, 11, 10, 0, 8, 12, 4, 13, 3, 7, 15, 14, 5, 6, 2, 4, 0,
            5, 9, 7, 12, 2, 10, 14, 1, 3, 8, 11, 6, 15, 13};

    // 右通道消息字排列
    private static final int[] RR = {5, 14, 7, 0, 9, 2, 11, 4, 13, 6, 15, 8, 1, 10, 3, 12,
            6, 11, 3, 7, 0, 13, 5, 10, 14, 15, 8, 12, 4, 9, 1, 2, 15, 5, 1, 3, 7, 14, 6, 9, 11,
            8, 12, 2, 10, 0, 4, 13, 8, 6, 4, 1, 3, 11, 15, 0, 5, 12, 2, 13, 9, 7, 10, 14, 12,
            15, 10, 4, 1, 5, 8, 7, 6, 2, 13, 14, 0, 3, 9, 11};

    // 左通道循环位移量
    private static final int[] SL = {11, 14, 15, 12, 5, 8, 7, 9, 11, 13, 14, 15, 6, 7, 9,
            8, 7, 6, 8, 13, 11, 9, 7, 15, 7, 12, 15, 9, 11, 7, 13, 12, 11, 13, 6, 7, 14, 9, 13,
            15, 14, 8, 13, 6, 5, 12, 7, 5, 11, 12, 14, 15, 14, 15, 9, 8, 9, 14, 5, 6, 8, 6, 5,
            12, 9, 15, 5, 11, 6, 8, 13, 12, 5, 12, 13, 14, 11, 8, 5, 6};

    // 右通道循环位移量
    private static final int[] SR = {8, 9, 9, 11, 13, 15, 15, 5, 7, 7, 8, 11, 14, 14, 12,
            6, 9, 13, 15, 7, 12, 8, 9, 11, 7, 7, 12, 7, 6, 15, 13, 11, 9, 7, 15, 11, 8, 6, 6,
            14, 12, 13, 5, 14, 13, 13, 7, 5, 15, 5, 8, 11, 14, 14, 6, 14, 6, 9, 12, 9, 12, 5,
            15, 8, 8, 5, 12, 9, 12, 5, 14, 6, 8, 13, 6, 5, 15, 13, 11, 11};

    // 左右通道每轮常量
    private static final int[] KL = {0x00000000, 0x5A827999, 0x6ED9EBA1, 0x8F1BBCDC, 0xA953FD4E};
    private static final int[] KR = {0x50A28BE6, 0x5C4DD124, 0x6D703EF3, 0x7A6D76E9, 0x00000000};

    private int h0;
    private int h1;
    private int h2;
    private int h3;
    private int h4;

    private final int[] X = new int[16];
    private int xOff;

    private final byte[] xBuf = new byte[4];
    private int xBufOff;

    private long byteCount;

    public Ripemd160Digest() {
        reset();
    }

    @Override
    public String getAlgorithmName() {
        return "RIPEMD160";
    }

    @Override
    public int getDigestResultSize() {
        return 20;
    }

    @Override
    public int getInternalBlockLength() {
        return 64;
    }

    @Override
    public void update(byte in) {
        xBuf[xBufOff++] = in;
        if (xBufOff == 4) {
            processWord(xBuf, 0);
            xBufOff = 0;
        }
        byteCount++;
    }

    @Override
    public void update(byte[] in, int inOff, int len) {
        while (xBufOff != 0 && len > 0) {
            update(in[inOff]);
            inOff++;
            len--;
        }
        while (len >= 4) {
            processWord(in, inOff);
            inOff += 4;
            len -= 4;
            byteCount += 4;
        }
        while (len > 0) {
            update(in[inOff]);
            inOff++;
            len--;
        }
    }

    @Override
    public int doFinal(byte[] out, int outOff) {
        finish();
        wordToBytes(h0, out, outOff);
        wordToBytes(h1, out, outOff + 4);
        wordToBytes(h2, out, outOff + 8);
        wordToBytes(h3, out, outOff + 12);
        wordToBytes(h4, out, outOff + 16);
        reset();
        return 20;
    }

    @Override
    public void reset() {
        h0 = 0x67452301;
        h1 = 0xEFCDAB89;
        h2 = 0x98BADCFE;
        h3 = 0x10325476;
        h4 = 0xC3D2E1F0;
        xOff = 0;
        Arrays.fill(X, 0);
        Arrays.fill(xBuf, (byte) 0);
        xBufOff = 0;
        byteCount = 0;
    }

    // ===== 内部 =====

    private void finish() {
        long bitLength = (byteCount << 3);
        update((byte) 0x80);
        while (xBufOff != 0) {
            update((byte) 0);
        }
        processLength(bitLength);
        processBlock();
    }

    private void processLength(long bitLength) {
        if (xOff > 14) {
            processBlock();
        }
        X[14] = (int) (bitLength & 0xffffffffL);
        X[15] = (int) (bitLength >>> 32);
    }

    private void processWord(byte[] in, int inOff) {
        X[xOff++] = (in[inOff] & 0xff) | ((in[inOff + 1] & 0xff) << 8) | ((in[inOff + 2] & 0xff) << 16)
                | ((in[inOff + 3] & 0xff) << 24);
        if (xOff == 16) {
            processBlock();
        }
    }

    private void processBlock() {
        int a = h0;
        int b = h1;
        int c = h2;
        int d = h3;
        int e = h4;

        int aa = h0;
        int bb = h1;
        int cc = h2;
        int dd = h3;
        int ee = h4;

        int t;
        for (int j = 0; j < 80; j++) {
            int round = j / 16;

            // 左通道轮函数按 1→5 顺序；右通道反向（5→1）
            t = rotl(a + f(round + 1, b, c, d) + X[RL[j]] + KL[round], SL[j]) + e;
            a = e;
            e = d;
            d = rotl(c, 10);
            c = b;
            b = t;

            t = rotl(aa + f(5 - round, bb, cc, dd) + X[RR[j]] + KR[round], SR[j]) + ee;
            aa = ee;
            ee = dd;
            dd = rotl(cc, 10);
            cc = bb;
            bb = t;
        }

        t = h1 + c + dd;
        h1 = h2 + d + ee;
        h2 = h3 + e + aa;
        h3 = h4 + a + bb;
        h4 = h0 + b + cc;
        h0 = t;

        xOff = 0;
        Arrays.fill(X, 0);
    }

    private static int f(int group, int x, int y, int z) {
        if (group == 1) {
            return x ^ y ^ z;
        }
        if (group == 2) {
            return (x & y) | (~x & z);
        }
        if (group == 3) {
            return (x | ~y) ^ z;
        }
        if (group == 4) {
            return (x & z) | (y & ~z);
        }
        return x ^ (y | ~z);
    }

    private static int rotl(int x, int n) {
        return (x << n) | (x >>> (32 - n));
    }

    private static void wordToBytes(int word, byte[] out, int off) {
        out[off] = (byte) word;
        out[off + 1] = (byte) (word >>> 8);
        out[off + 2] = (byte) (word >>> 16);
        out[off + 3] = (byte) (word >>> 24);
    }

    @Override
    public AlgorithmFactory<? extends Digest> factory() {
        return FACTORY;
    }

    public static final AlgorithmFactory<Digest> FACTORY = new AlgorithmFactory<>() {
        @Override
        public Class<? extends AlgorithmFactoryRegister> registerTo() {
            return CryptoAlgorithmFactoryRegister.class;
        }

        @Override
        public Set<String> supportedAlgorithms() {
            return Set.of("Ripemd160", "RIPEMD160");
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public Class<AlgorithmComponent>[] componentTypes() {
            return new Class[0];
        }

        @Override
        public Digest construct(String algorithmName, AlgorithmComponent... components) {
            return new Ripemd160Digest();
        }
    };
}
