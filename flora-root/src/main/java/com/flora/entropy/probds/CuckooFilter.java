package com.flora.entropy.probds;

import com.flora.entropy.hash.MurmurHash;
import com.flora.tag.ThreadFragile;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 布谷鸟过滤器（Cuckoo Filter）——一种支持删除操作的概率性集合。
 * <p>
 * 相较于布隆过滤器，布谷鸟过滤器额外支持<strong>删除</strong>操作，
 * 且空间利用率更高（每个元素约 1-2 字节指纹）。
 * 适用于需要动态增删元素的去重场景。
 * </p>
 * <p>
 * 实现基于 Fan 等人的 Cuckoo Filter 论文（NSDI 2014）：
 * <ul>
 *   <li>每个桶（bucket）存储 b 个指纹（fingerprint）</li>
 *   <li>每个元素有两个候选桶位置</li>
 *   <li>插入冲突时执行布谷鸟踢出（cuckoo eviction）</li>
 * </ul>
 * </p>
 */
@ThreadFragile
public final class CuckooFilter {

    private final int numBuckets;      // 桶数（必须为 2 的幂）
    private final int bucketMask;      // 桶数掩码
    private final int fingerprintBits; // 指纹长度（位）
    private final int fingerprintMask; // 指纹掩码
    private final int entriesPerBucket;// 每桶指纹数 b
    private final short[][] buckets;   // 桶数组
    private final byte[] counts;       // 每桶已用空间
    private int size;                  // 当前元素数

    /** 最大踢出次数（防止无限循环）。 */
    private static final int MAX_KICKS = 500;

    /**
     * 构造布谷鸟过滤器。
     *
     * @param expectedInsertions 期望插入元素数量
     * @param fpp                期望误报率（0 &lt; fpp &lt; 1）
     */
    public CuckooFilter(int expectedInsertions, double fpp) {
        if (expectedInsertions <= 0 || fpp <= 0 || fpp >= 1) {
            throw new IllegalArgumentException("expectedInsertions > 0, 0 < fpp < 1");
        }
        // 指纹位 = ceil(log2(1/fpp) + log2(2*b))
        this.entriesPerBucket = 4;
        this.fingerprintBits = (int) Math.ceil(Math.log(1.0 / fpp) / Math.log(2)) + 3;
        this.fingerprintMask = (1 << Math.min(fingerprintBits, 16)) - 1;

        // 桶数 = ceil(expectedInsertions / entriesPerBucket / loadFactor)
        double loadFactor = 0.95;
        int rawBuckets = (int) Math.ceil(expectedInsertions / entriesPerBucket / loadFactor);
        // 向上取整到 2 的幂
        this.numBuckets = Integer.highestOneBit(rawBuckets) << 1;
        this.bucketMask = numBuckets - 1;

        this.buckets = new short[numBuckets][entriesPerBucket];
        this.counts = new byte[numBuckets];
        this.size = 0;
    }

    /**
     * 插入元素。
     *
     * @param data 元素的字节表示
     * @return true 插入成功，false 插入失败（过滤器已满）
     */
    public boolean put(byte[] data) {
        return putFingerprint(hashFingerprint(data), getBucketIndex(data));
    }

    /**
     * 插入字符串元素（UTF-8 编码）。
     *
     * @param s 字符串元素
     * @return true 插入成功
     */
    public boolean put(String s) {
        return put(s == null ? new byte[0] : s.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 判断元素可能存在于过滤器中。
     *
     * @param data 元素的字节表示
     * @return false 一定不存在，true 可能存在
     */
    public boolean mightContain(byte[] data) {
        short fp = hashFingerprint(data);
        int i1 = getBucketIndex(data);
        return containsInBucket(i1, fp) || containsInBucket(alternateIndex(i1, fp), fp);
    }

    /**
     * 判断字符串元素可能存在于过滤器中。
     *
     * @param s 字符串元素
     * @return false 一定不存在，true 可能存在
     */
    public boolean mightContain(String s) {
        return mightContain(s == null ? new byte[0] : s.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 删除元素。
     *
     * @param data 元素的字节表示
     * @return true 删除成功，false 元素不存在
     */
    public boolean delete(byte[] data) {
        short fp = hashFingerprint(data);
        int i1 = getBucketIndex(data);
        if (removeFromBucket(i1, fp)) {
            size--;
            return true;
        }
        int i2 = alternateIndex(i1, fp);
        if (removeFromBucket(i2, fp)) {
            size--;
            return true;
        }
        return false;
    }

    /**
     * 删除字符串元素。
     *
     * @param s 字符串元素
     * @return true 删除成功
     */
    public boolean delete(String s) {
        return delete(s == null ? new byte[0] : s.getBytes(StandardCharsets.UTF_8));
    }

    /** 返回当前元素数量。 */
    public int size() { return size; }

    /** 返回桶数量。 */
    public int bucketCount() { return numBuckets; }

    /** 返回每个桶的指纹数。 */
    public int entriesPerBucket() { return entriesPerBucket; }

    /** 返回指纹长度（位）。 */
    public int fingerprintBits() { return fingerprintBits; }

    // ==================== 内部方法 ====================

    private boolean putFingerprint(short fp, int bucketIndex) {
        // 尝试插入第一个桶
        if (insertIntoBucket(bucketIndex, fp)) {
            size++;
            return true;
        }
        // 尝试备用桶
        int curBucket = alternateIndex(bucketIndex, fp);
        if (insertIntoBucket(curBucket, fp)) {
            size++;
            return true;
        }

        // 两个桶都满，执行布谷鸟踢出
        // 随机选择一个桶开始踢出
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        short curFp = fp;
        for (int i = 0; i < MAX_KICKS; i++) {
            // 从当前桶中随机踢出一个指纹
            int victimSlot = rng.nextInt(entriesPerBucket);
            short victimFp = buckets[curBucket][victimSlot];
            buckets[curBucket][victimSlot] = curFp;

            // 被踢出者去往它的备用桶
            curBucket = alternateIndex(curBucket, curFp);
            curFp = victimFp;

            if (insertIntoBucket(curBucket, curFp)) {
                size++;
                return true;
            }
        }
        // 达到最大踢出次数，空间不足
        return false;
    }

    private boolean insertIntoBucket(int bucketIndex, short fp) {
        short[] bucket = buckets[bucketIndex];
        int c = counts[bucketIndex];
        if (c < entriesPerBucket) {
            bucket[c] = fp;
            counts[bucketIndex] = (byte) (c + 1);
            return true;
        }
        return false;
    }

    private boolean containsInBucket(int bucketIndex, short fp) {
        short[] bucket = buckets[bucketIndex];
        int c = counts[bucketIndex];
        for (int i = 0; i < c; i++) {
            if (bucket[i] == fp) {
                return true;
            }
        }
        return false;
    }

    private boolean removeFromBucket(int bucketIndex, short fp) {
        short[] bucket = buckets[bucketIndex];
        int c = counts[bucketIndex];
        for (int i = 0; i < c; i++) {
            if (bucket[i] == fp) {
                // 用最后一个元素填补空缺
                bucket[i] = bucket[c - 1];
                bucket[c - 1] = 0;
                counts[bucketIndex] = (byte) (c - 1);
                return true;
            }
        }
        return false;
    }

    /** 计算指纹。 */
    private short hashFingerprint(byte[] data) {
        int hash = MurmurHash.mmh3(data);
        int fp = hash & fingerprintMask;
        // 指纹不能为 0（0 用于标示空槽），最小值为 1
        if (fp == 0) fp = 1;
        return (short) fp;
    }

    /** 计算第一个桶索引。 */
    private int getBucketIndex(byte[] data) {
        int hash = MurmurHash.mmh3(data);
        return (hash >>> 16) & bucketMask; // 使用高位避免与指纹哈希相关
    }

    /** 计算备用桶索引：i2 = i1 XOR H(fp)。 */
    private int alternateIndex(int bucketIndex, short fp) {
        int mix = MurmurHash.mmh3(fp) & bucketMask;
        return (bucketIndex ^ mix) & bucketMask;
    }

    // ==================== 工厂 ====================

    /**
     * 创建默认配置的布谷鸟过滤器（期望 100 万元素，1% 误报率）。
     */
    public static CuckooFilter withDefaultConfig() {
        return new CuckooFilter(1_000_000, 0.01);
    }
}
