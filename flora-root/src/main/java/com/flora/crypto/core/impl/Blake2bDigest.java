package com.flora.crypto.core.impl;

import com.flora.common.algorithm.AlgorithmComponent;
import com.flora.common.algorithm.AlgorithmConstant;
import com.flora.common.algorithm.AlgorithmFactory;
import com.flora.common.algorithm.AlgorithmFamilyRegister;
import com.flora.crypto.core.CryptoAlgorithmFamilyRegister;
import com.flora.crypto.core.interfaces.algorithm.Digest;
import com.flora.java.CheckUtil;

import java.util.Arrays;
import java.util.Set;

/**
 * BLAKE2b 摘要（RFC 7693）。
 * <p>块 128 字节，输出长度可配置（构造参数 {@code digestBytes}，1..64）。Argon2 依赖本实现
 * 作为其底层哈希（H0 / 内存填充 G 函数基于 BLAKE2b 轮）。</p>
 */
public final class Blake2bDigest implements Digest {

    private static final int BLOCK_BYTES = 128;

    // RFC 7693 §2.5：与 SHA-512 相同的初始化向量
    private static final long[] IV = {0x6a09e667f3bcc908L, 0xbb67ae8584caa73bL, 0x3c6ef372fe94f82bL,
            0xa54ff53a5f1d36f1L, 0x510e527fade682d1L, 0x9b05688c2b3e6c1fL, 0x1f83d9abfb41bd6bL,
            0x5be0cd19137e2179L};

    // RFC 7693 §2.7：12 轮消息置换
    private static final int[][] SIGMA = {
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15},
            {14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3},
            {11, 8, 12, 0, 5, 2, 15, 13, 10, 14, 3, 6, 7, 1, 9, 4},
            {7, 9, 3, 1, 13, 12, 11, 14, 2, 6, 5, 10, 4, 0, 15, 8},
            {9, 0, 5, 7, 2, 4, 10, 15, 14, 1, 11, 12, 6, 8, 3, 13},
            {2, 12, 6, 10, 0, 11, 8, 3, 4, 13, 7, 5, 15, 14, 1, 9},
            {12, 5, 1, 15, 14, 13, 4, 10, 0, 7, 6, 3, 9, 2, 8, 11},
            {13, 11, 7, 14, 12, 1, 3, 9, 5, 0, 15, 4, 8, 6, 2, 10},
            {6, 15, 14, 9, 11, 3, 0, 8, 12, 2, 13, 7, 1, 4, 10, 5},
            {10, 2, 8, 4, 7, 6, 1, 5, 15, 11, 9, 14, 3, 12, 13, 0},
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15},
            {14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3}};

    // G 函数行列映射：(a,b,c,d) 在 v[] 中的下标
    private static final int[][] ROW = {
            {0, 4, 8, 12}, {1, 5, 9, 13}, {2, 6, 10, 14}, {3, 7, 11, 15},
            {0, 5, 10, 15}, {1, 6, 11, 12}, {2, 7, 8, 13}, {3, 4, 9, 14}};

    private final long[] h = new long[8];
    private final long[] t = new long[2];
    private boolean lastBlock;
    private final byte[] m = new byte[BLOCK_BYTES];
    private int mOff;

    private final int digestBytes;

    public Blake2bDigest(int digestBytes) {
        if (digestBytes < 1 || digestBytes > 64) {
            throw new IllegalArgumentException("BLAKE2b 输出长度须在 [1,64] 字节: " + digestBytes);
        }
        this.digestBytes = digestBytes;
        reset();
    }

    /** BLAKE2b-256。 */
    public static Blake2bDigest of256() {
        return new Blake2bDigest(32);
    }

    /** BLAKE2b-512。 */
    public static Blake2bDigest of512() {
        return new Blake2bDigest(64);
    }

    @Override
    public String getAlgorithmName() {
        return "BLAKE2B-" + (digestBytes * 8);
    }

    @Override
    public int getDigestResultSize() {
        return digestBytes;
    }

    @Override
    public int getInternalBlockLength() {
        return BLOCK_BYTES;
    }

    @Override
    public void update(byte in) {
        if (mOff == BLOCK_BYTES) {
            compress(BLOCK_BYTES);
            mOff = 0;
        }
        m[mOff++] = in;
    }

    @Override
    public void update(byte[] in, int inOff, int len) {
        while (len > 0) {
            if (mOff == BLOCK_BYTES) {
                compress(BLOCK_BYTES);
                mOff = 0;
            }
            int n = Math.min(BLOCK_BYTES - mOff, len);
            System.arraycopy(in, inOff, m, mOff, n);
            mOff += n;
            inOff += n;
            len -= n;
        }
    }

    @Override
    public int doFinal(byte[] out, int outOff) {
        int finalLen = mOff;
        Arrays.fill(m, mOff, BLOCK_BYTES, (byte) 0);
        lastBlock = true;
        compress(finalLen);

        for (int i = 0; i < digestBytes; i++) {
            out[outOff + i] = (byte) (h[i / 8] >>> (8 * (i % 8)));
        }
        reset();
        return digestBytes;
    }

    @Override
    public void reset() {
        System.arraycopy(IV, 0, h, 0, 8);
        h[0] ^= 0x01010000L ^ (long) digestBytes;
        t[0] = 0;
        t[1] = 0;
        lastBlock = false;
        Arrays.fill(m, (byte) 0);
        mOff = 0;
    }

    // ===== 内部 =====

    private void compress(int numBytes) {
        long prev = t[0];
        t[0] += numBytes;
        if (t[0] < prev) {
            t[1]++;
        }

        long[] v = new long[16];
        System.arraycopy(h, 0, v, 0, 8);
        System.arraycopy(IV, 0, v, 8, 8);
        v[12] ^= t[0];
        v[13] ^= t[1];
        if (lastBlock) {
            v[14] = ~v[14];
        }

        long[] x = new long[16];
        for (int i = 0; i < 16; i++) {
            x[i] = m[i * 8] & 0xffL | (m[i * 8 + 1] & 0xffL) << 8 | (m[i * 8 + 2] & 0xffL) << 16
                    | (m[i * 8 + 3] & 0xffL) << 24 | (m[i * 8 + 4] & 0xffL) << 32
                    | (m[i * 8 + 5] & 0xffL) << 40 | (m[i * 8 + 6] & 0xffL) << 48
                    | (m[i * 8 + 7] & 0xffL) << 56;
        }

        for (int r = 0; r < 12; r++) {
            for (int i = 0; i < 8; i++) {
                g(r, i, v, x);
            }
        }

        for (int i = 0; i < 8; i++) {
            h[i] ^= v[i] ^ v[i + 8];
        }
    }

    private static void g(int r, int i, long[] v, long[] x) {
        int[] p = ROW[i];
        int[] s = SIGMA[r];
        v[p[0]] += v[p[1]] + x[s[2 * i]];
        v[p[3]] = Long.rotateRight(v[p[3]] ^ v[p[0]], 32);
        v[p[2]] += v[p[3]];
        v[p[1]] = Long.rotateRight(v[p[1]] ^ v[p[2]], 24);
        v[p[0]] += v[p[1]] + x[s[2 * i + 1]];
        v[p[3]] = Long.rotateRight(v[p[3]] ^ v[p[0]], 16);
        v[p[2]] += v[p[3]];
        v[p[1]] = Long.rotateRight(v[p[1]] ^ v[p[2]], 63);
    }

    @Override
    public AlgorithmFactory<? extends Digest> factory() {
        return FACTORY;
    }

    public static final AlgorithmFactory<Digest> FACTORY = new AlgorithmFactory<>() {
        @Override
        public Class<? extends AlgorithmFamilyRegister> registerTo() {
            return CryptoAlgorithmFamilyRegister.class;
        }

        @Override
        public Set<String> supportedAlgorithms() {
            return Set.of("Blake2b", "BLAKE2B-256", "BLAKE2B-512");
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public Class<AlgorithmComponent>[] componentTypes() {
            return new Class[]{Integer.class};
        }

        @Override
        public Digest construct(String algorithmName, AlgorithmComponent... components) {
            CheckUtil.notNull(algorithmName, "算法名不能为空");
            return switch (algorithmName) {
                case "BLAKE2B-256" -> Blake2bDigest.of256();
                case "BLAKE2B-512" -> Blake2bDigest.of512();
                default -> {
                    int outBytes = components.length > 0
                            ? ((AlgorithmConstant<Integer>) components[0]).getValue() : 64;
                    yield new Blake2bDigest(outBytes);
                }
            };
        }
    };
}
