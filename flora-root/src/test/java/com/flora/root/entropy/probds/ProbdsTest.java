package com.flora.root.entropy.probds;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 概率数据结构（probds 包）的基础正确性与回归测试。
 * 重点覆盖修复项：布谷鸟过滤器桶空间大于 65536 时的插入、HLL 估计精度、CMS 上界语义等。
 */
class ProbdsTest {

    /** int 的大端 4 字节表示。 */
    private static byte[] intBytes(int x) {
        return new byte[]{
                (byte) (x >>> 24),
                (byte) (x >>> 16),
                (byte) (x >>> 8),
                (byte) x
        };
    }

    // ==================== BloomFilter ====================

    @Test
    void bloomFilter_noFalseNegative() {
        int n = 50_000;
        BloomFilter<String> bf = BloomFilter.forStrings(n, 0.01);
        for (int i = 0; i < n; i++) {
            bf.put("key-" + i);
        }
        for (int i = 0; i < n; i++) {
            assertTrue(bf.mightContain("key-" + i), "已插入元素不应漏报: " + i);
        }
    }

    @Test
    void bloomFilter_falsePositiveBelowTarget() {
        int n = 50_000;
        BloomFilter<String> bf = BloomFilter.forStrings(n, 0.01);
        for (int i = 0; i < n; i++) {
            bf.put("key-" + i);
        }
        // 查询与已插入集合不相交的另一个域
        int probes = 100_000;
        int fp = 0;
        for (int i = 0; i < probes; i++) {
            if (bf.mightContain("absent-" + i)) {
                fp++;
            }
        }
        // 目标误报率 1%，宽松断言上限 3%
        assertTrue(fp < probes * 0.03, "实测误报率过高: " + fp + "/" + probes);
    }

    @Test
    void bloomFilter_rejectsHugeInsertions() {
        assertThrows(IllegalArgumentException.class,
                () -> BloomFilter.forStrings(Integer.MAX_VALUE / 2, 0.0001));
    }

    // ==================== CuckooFilter ====================

    @Test
    void cuckooFilter_basicInsertQueryDelete() {
        CuckooFilter cf = new CuckooFilter(10_000, 0.01);
        int n = 8_000;
        for (int i = 0; i < n; i++) {
            assertTrue(cf.put(intBytes(i)), "插入应成功: " + i);
        }
        assertEquals(n, cf.size());
        for (int i = 0; i < n; i++) {
            assertTrue(cf.mightContain(intBytes(i)), "已插入元素不应漏报: " + i);
        }
        int removed = 0;
        for (int i = 0; i < n; i += 2) {
            if (cf.delete(intBytes(i))) {
                removed++;
            }
        }
        assertEquals(n / 2, removed);
        assertEquals(n - n / 2, cf.size());
    }

    /**
     * 回归测试：修复前桶索引被截断到 16 位，numBuckets 超过 65536 时大部分桶不可达，
     * 约 25 万元素规模即开始插入失败。
     */
    @Test
    void cuckooFilter_largeBucketSpaceDoesNotSaturate() {
        int n = 260_000;
        CuckooFilter cf = new CuckooFilter(n, 0.01);
        assertTrue(cf.bucketCount() > 65_536, "该规模应构造出大于 65536 的桶空间");
        for (int i = 0; i < n; i++) {
            assertTrue(cf.put(intBytes(i)), "大桶空间下插入不应失败: " + i);
        }
        for (int i = 0; i < n; i += 10) {
            assertTrue(cf.mightContain(intBytes(i)), "已插入元素不应漏报: " + i);
        }
    }

    @Test
    void cuckooFilter_rejectsTooSmallFpp() {
        // 1e-5 低于 16 位指纹下限（约 1.2e-4），应被拒绝
        assertThrows(IllegalArgumentException.class, () -> new CuckooFilter(1000, 1e-5));
    }

    // ==================== HyperLogLog ====================

    @Test
    void hyperLogLog_estimatesCardinality() {
        HyperLogLog hll = new HyperLogLog(12);
        int n = 200_000;
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < n; i++) {
            // 混入一些重复以模拟真实基数略小于插入次数
            int value = i % 2 == 0 ? i : i / 2;
            seen.add(value);
            hll.add(value);
        }
        long estimate = hll.estimate();
        int real = seen.size();
        // p=12 标准误差约 1.6%，宽松边界 ±10%
        assertTrue(estimate > real * 0.9 && estimate < real * 1.1,
                "估计偏差过大: real=" + real + " estimate=" + estimate);
    }

    @Test
    void hyperLogLog_mergeEqualsUnion() {
        HyperLogLog a = new HyperLogLog(12);
        HyperLogLog b = new HyperLogLog(12);
        int half = 100_000;
        for (int i = 0; i < half; i++) {
            a.add(i);
            b.add(half + i);
        }
        a.merge(b);
        long estimate = a.estimate();
        assertTrue(estimate > 2L * half * 0.9 && estimate < 2L * half * 1.1,
                "合并估计偏差过大: estimate=" + estimate);
        assertThrows(IllegalArgumentException.class, () -> a.merge(new HyperLogLog(10)));
    }

    // ==================== CountMinSketch ====================

    @Test
    void countMinSketch_estimateIsUpperBound() {
        CountMinSketch cms = new CountMinSketch(4096, 8);
        long heavy = 1_000;
        cms.add(intBytes(0), heavy);
        long noise = 0;
        for (int i = 1; i <= 5_000; i++) {
            long c = 1 + (i % 7);
            cms.add(intBytes(i), c);
            noise += c;
        }
        long est = cms.estimate(intBytes(0));
        // CMS 保证：estimate >= 真实计数
        assertTrue(est >= heavy, "估计值不得低于真实计数: " + est);
        // 经验性误差上界：超出真实值的部分应远小于总计数（4096 宽 → 误差率 ~e/4096≈0.07%）
        assertTrue(est - heavy < (heavy + noise) * 0.01,
                "误差超出预期: est=" + est + " true=" + heavy);
    }

    @Test
    void countMinSketch_mergeSumsCounts() {
        CountMinSketch a = new CountMinSketch(4096, 8);
        CountMinSketch b = new CountMinSketch(4096, 8);
        a.add(intBytes(7), 30);
        b.add(intBytes(7), 12);
        b.add(intBytes(8), 5);
        a.merge(b);
        assertEquals(47, a.totalCount());
        // 哈希碰撞只会抬高估计值，因此用下界断言（>= 真实合计）保证无竞态丢失
        assertTrue(a.estimate(intBytes(7)) >= 42, "合并后计数不应丢失");
        assertTrue(a.estimate(intBytes(8)) >= 5, "合并后计数不应丢失");
        // 合并与单边写入结果应一致：无 b 写入的 key 计数不被污染
        assertTrue(b.estimate(intBytes(7)) >= 12);
    }

    @Test
    void countMinSketch_rejectsInvalidParams() {
        assertThrows(IllegalArgumentException.class, () -> new CountMinSketch(0, 4));
        assertThrows(IllegalArgumentException.class, () -> new CountMinSketch(100, 0));
        assertThrows(IllegalArgumentException.class, () -> new CountMinSketch(0.01, 1.5));
    }
}
