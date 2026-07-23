package com.flora.entropy.hash;


import static com.flora.entropy.hash.UsageScenario.*;

public final class GoldenRatioMix {
    private static final long GOLDEN_RATIO_LONG=0x9E3779B97F4A7C15L;
    private static final int GOLDEN_RATIO_INT=0x9E3779B9;
    private static final short GOLDEN_RATIO_SHORT= (short) 0x9E37;
    private static final byte GOLDEN_RATIO_BYTE= (byte) 0x9E;
    /**
     * 对 long 值执行 goldenHash 混合（基于黄金分割比乘法）。
     * <p>适用于快速哈希和地址映射场景。</p>
     *
     * @param x 输入值
     * @return 混合后的 long 值
     */
    @SuitedFor({FAST, ADDRESSING})
    public static long goldenHash(long x) {
        long h = x * GOLDEN_RATIO_LONG;
        h ^= h >>> 32;
        return h ^ (h >>> 16);
    }

    /**
     * 对 int 值执行 goldenHash 。
     *
     * @param x 输入值
     * @return 混合后的 int 值
     */
    @SuitedFor({FAST, ADDRESSING})
    public static int goldenHash(int x) {
        int h = x * GOLDEN_RATIO_INT;
        return h ^ (h >>> 16);
    }

    /**
     * 对 short 值执行 goldenHash 。
     *
     * @param x 输入值
     * @return 混合后的 short 值
     */
    @SuitedFor({FAST, ADDRESSING})
    public static short goldenHash(short x) {
        return (short) (x*GOLDEN_RATIO_SHORT);
    }

    /**
     * 对 byte 值执行 goldenHash 。
     *
     * @param x 输入值
     * @return 混合后的 byte 值
     */
    @SuitedFor({FAST, ADDRESSING})
    public static byte goldenHash(byte x) {
        return (byte) (x*GOLDEN_RATIO_BYTE);
    }
}
