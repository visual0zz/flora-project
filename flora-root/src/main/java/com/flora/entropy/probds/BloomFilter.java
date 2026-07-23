package com.flora.entropy.probds;

import com.flora.entropy.hash.MurmurHash;
import com.flora.tag.ThreadFragile;

import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.function.Function;

/**
 * 布隆过滤器（Bloom Filter）——一种空间高效的概率性集合。
 * <p>
 * 用于判断一个元素是否"可能存在于集合中"（存在误报率 false positive），
 * 但绝不会漏报（false negative 为零）。适用于缓存穿透防护、爬虫去重、
 * 垃圾邮件过滤、数据库布隆索引等场景。
 * </p>
 * <p>
 * 内部使用双哈希技巧（Kirsch-Mitzenmacker 优化）：以 mmh3(data) 作为 h1，
 * 再以 h1 的 4 字节表示二次哈希得到 h2，第 i 个哈希位置由 h1 + i × h2 生成。
 * 位数组使用 {@link BitSet} 实现。
 * </p>
 */
@ThreadFragile
public final class BloomFilter<T> {

    private final BitSet bits;
    private final int bitCount;   // 位数组大小 m
    private final int hashCount;  // 哈希函数数量 k
    private final Function<T, byte[]> toBytes;

    /**
     * 构造布隆过滤器。
     *
     * @param expectedInsertions 期望插入元素数量 n
     * @param fpp               期望误报率（0 &lt; fpp &lt; 1），如 0.01 表示 1%
     * @param toBytes           将元素转换为字节数组的函数
     */
    public BloomFilter(int expectedInsertions, double fpp, Function<T, byte[]> toBytes) {
        if (expectedInsertions <= 0 || fpp <= 0 || fpp >= 1) {
            throw new IllegalArgumentException("expectedInsertions > 0, 0 < fpp < 1");
        }
        this.toBytes = toBytes;
        // m = -n * ln(p) / (ln2)^2
        this.bitCount = optimalBitCount(expectedInsertions, fpp);
        // k = (m / n) * ln2
        this.hashCount = optimalHashCount(expectedInsertions, bitCount);
        this.bits = new BitSet(bitCount);
    }

    /**
     * 构造布隆过滤器（默认误报率 1%）。
     *
     * @param expectedInsertions 期望插入元素数量
     * @param toBytes           将元素转换为字节数组的函数
     */
    public BloomFilter(int expectedInsertions, Function<T, byte[]> toBytes) {
        this(expectedInsertions, 0.01, toBytes);
    }

    /**
     * 将元素插入布隆过滤器。
     *
     * @param item 待插入元素
     */
    public void put(T item) {
        byte[] data = toBytes.apply(item);
        int h1 = MurmurHash.mmh3(data);
        int h2 = MurmurHash.mmh3(mmh3AsBytes(h1));
        // 取绝对值，避免负数下标
        long hash1 = absLong(h1);
        long hash2 = absLong(h2) | 1L; // 确保为奇数，保证覆盖所有位
        for (int i = 0; i < hashCount; i++) {
            int pos = (int) ((hash1 + (long) i * hash2) % bitCount);
            bits.set(pos);
        }
    }

    /**
     * 判断元素是否可能存在于集合中。
     *
     * @param item 待检测元素
     * @return false 表示一定不存在，true 表示可能存在（存在误报）
     */
    public boolean mightContain(T item) {
        byte[] data = toBytes.apply(item);
        int h1 = MurmurHash.mmh3(data);
        int h2 = MurmurHash.mmh3(mmh3AsBytes(h1));
        long hash1 = absLong(h1);
        long hash2 = absLong(h2) | 1L;
        for (int i = 0; i < hashCount; i++) {
            int pos = (int) ((hash1 + (long) i * hash2) % bitCount);
            if (!bits.get(pos)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 返回当前估计的误报率。
     *
     * @return 基于已置位比例的近似误报率 (setBits / m)^k
     */
    public double estimatedFpp() {
        long setBits = bits.cardinality();
        double ratio = (double) setBits / bitCount;
        return Math.pow(ratio, hashCount);
    }

    /** 返回位数组大小 m。 */
    public int bitCount() { return bitCount; }

    /** 返回哈希函数数量 k。 */
    public int hashCount() { return hashCount; }

    /** 返回当前已置位的数量。 */
    public long setBitCount() { return bits.cardinality(); }

    // ==================== 内部工具 ====================

    private static int optimalBitCount(int n, double p) {
        return (int) Math.ceil(-n * Math.log(p) / (Math.log(2) * Math.log(2)));
    }

    private static int optimalHashCount(int n, int m) {
        // 至少 1 个哈希函数
        return Math.max(1, (int) Math.round((double) m / n * Math.log(2)));
    }

    private static long absLong(int x) {
        // Integer.MIN_VALUE 的特殊处理
        return x == Integer.MIN_VALUE ? (1L << 31) : (long) Math.abs(x);
    }

    private static byte[] mmh3AsBytes(int x) {
        return new byte[]{
                (byte) (x >>> 24),
                (byte) (x >>> 16),
                (byte) (x >>> 8),
                (byte) x
        };
    }

    // ==================== 常用工厂方法 ====================

    /** 为 String 元素创建布隆过滤器（UTF-8 编码）。 */
    public static BloomFilter<String> forStrings(int expectedInsertions, double fpp) {
        return new BloomFilter<>(expectedInsertions, fpp,
                s -> s == null ? new byte[0] : s.getBytes(StandardCharsets.UTF_8));
    }

    /** 为 String 元素创建布隆过滤器（默认 1% 误报率）。 */
    public static BloomFilter<String> forStrings(int expectedInsertions) {
        return forStrings(expectedInsertions, 0.01);
    }

    /** 为 byte[] 元素创建布隆过滤器。 */
    public static BloomFilter<byte[]> forBytes(int expectedInsertions, double fpp) {
        return new BloomFilter<>(expectedInsertions, fpp, Function.identity());
    }
}
