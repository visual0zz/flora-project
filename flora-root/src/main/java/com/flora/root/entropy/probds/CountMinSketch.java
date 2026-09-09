package com.flora.root.entropy.probds;

import com.flora.root.entropy.HashUtil;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Count-Min Sketch（CMS）——一种频率估计算法。
 * <p>
 * 使用固定大小的二维计数器矩阵，估计数据流中元素的出现频率。
 * 查询返回的是真实计数的上界（总是高估或等于真实值，不会低估）。
 * 适用于 Top-K 统计、网络流量监控、热点检测等场景。
 * </p>
 * <p>
 * 线程安全约定：{@link #add} 与 {@link #estimate} 内部对单元格同步，可安全并发；
 * {@link #merge} 读取 other 时不加锁，因此 merge 不能与任何一方实例上的并发写入
 * （add/merge）同时进行，需要调用方自行保证。
 * </p>
 */

public final class CountMinSketch {
    private final int depth;
    private final int width;
    private final long[][] table;
    private final long[] seeds;
    private final AtomicLong totalCount;

    /**
     * 创建默认参数的 CMS：误差 0.1%（ε=0.001），置信度 99.9%（δ=0.001）。
     * 宽度 ≈ 2719，深度 = 7，内存 ≈ 149KB。
     */
    public CountMinSketch() {
        this(0.001, 0.001);
    }

    /**
     * 构造 Count-Min Sketch。
     * @param epsilon 相对误差（值越小精度越高，宽度越大），如 0.01
     * @param delta   置信度（值越小置信度越高，深度越大），如 0.01
     */
    public CountMinSketch(double epsilon, double delta) {
        if (!(epsilon > 0) || !(Double.isFinite(epsilon))
                || !(delta > 0 && delta < 1)) {
            throw new IllegalArgumentException("epsilon > 0 且有限, 0 < delta < 1");
        }
        // w = ceil(e / epsilon)，d = ceil(ln(1 / delta))
        double w = Math.ceil(Math.E / epsilon);
        double d = Math.ceil(Math.log(1.0 / delta));
        if (!(w <= Integer.MAX_VALUE) || !(d <= Integer.MAX_VALUE)) {
            throw new IllegalArgumentException("epsilon/delta 过小，矩阵维度超出 int 上限");
        }
        this((int) w, (int) d);
    }

    /**
     * 创建指定宽度和深度的 Count-Min Sketch。
     * <p>width 决定误差 ε（越大精度越高，内存线性增长），
     * depth 决定置信度 1-δ（越大置信度越高）。</p>
     * <p>内存 = {@code width × depth × Long.BYTES} 字节。</p>
     * @param width  二维矩阵的列数（w），对应哈希空间大小，
     *               取值为期望相对误差 ε 的倒数 × e
     * @param depth  二维矩阵的行数（d），对应哈希函数数量，
     *               取值为 ln(1 / δ)
     */
    public CountMinSketch(int width, int depth) {
        if (!(width > 0) || !(depth > 0)) {
            throw new IllegalArgumentException("width 和 depth 都必须为正数");
        }
        this.width = width;
        this.depth = depth;
        this.table = new long[width][depth];
        // 为每一行生成独立种子
        this.seeds = new long[depth];
        for (int i = 0; i < depth; i++) {
            seeds[i] = HashUtil.goldenHash(i);
        }
        this.totalCount = new AtomicLong(0);
    }

    /**
     * 增加元素的出现次数。
     *
     * @param data  元素的字节表示
     * @param count 增加量（必须 &ge; 0）
     */
    public void add(byte[] data, long count) {
        if (count < 0) {
            throw new IllegalArgumentException("count 不能为负数: " + count);
        }
        totalCount.addAndGet(count);
        for (int i = 0; i < depth; i++) {
            int pos = hash(data, seeds[i]) % width;
            synchronized (table[pos]){
                table[pos][i] += count;
            }
        }
    }

    /**
     * 增加元素的出现次数（计数 1）。
     *
     * @param data 元素的字节表示
     */
    public void add(byte[] data) {
        add(data, 1);
    }

    /**
     * 增加字符串元素的出现次数（UTF-8 编码）。
     *
     * @param s     字符串元素
     * @param count 增加量
     */
    public void add(String s, long count) {
        add(s == null ? new byte[0] : s.getBytes(StandardCharsets.UTF_8), count);
    }

    /**
     * 增加字符串元素的出现次数（计数 1）。
     *
     * @param s 字符串元素
     */
    public void add(String s) {
        add(s, 1);
    }

    /**
     * 估计元素的出现频率。
     *
     * @param data 元素的字节表示
     * @return 频率估计值（真实值的上界）
     */
    public long estimate(byte[] data) {
        long min = Long.MAX_VALUE;
        for (int i = 0; i < depth; i++) {
            int pos = hash(data, seeds[i]) % width;
            synchronized (table[pos]){
                min = Math.min(min, table[pos][i]);
            }
        }
        return min;
    }

    /**
     * 估计字符串元素的出现频率。
     *
     * @param s 字符串元素
     * @return 频率估计值
     */
    public long estimate(String s) {
        return estimate(s == null ? new byte[0] : s.getBytes(StandardCharsets.UTF_8));
    }

    /** 返回当前总计数。 */
    public long totalCount() { return totalCount.get(); }

    /** 返回深度（行数）。 */
    public int depth() { return depth; }

    /** 返回宽度（列数）。 */
    public int width() { return width; }

    /** 返回内存占用（字节）。 */
    public long memoryBytes() {
        return (long) depth * width * Long.BYTES;
    }

    /**
     * 合并另一个 Count-Min Sketch（用于分布式频率汇总）。
     *
     * @param other 另一个同尺寸的 CMS 实例
     */
    public void merge(CountMinSketch other) {
        if (this.depth != other.depth || this.width != other.width) {
            throw new IllegalArgumentException("维度不一致，无法合并");
        }
        for (int j = 0; j < width; j++) {
            synchronized (table[j]){
                for (int i = 0; i < depth; i++) {
                    this.table[j][i] += other.table[j][i];
                }
            }
        }
        this.totalCount.addAndGet(other.totalCount.get());
    }

    // ==================== 内部工具 ====================

    /** 对字节数组和种子哈希，返回 0 ~ width 范围内的值。 */
    private static int hash(byte[] data, long seed) {
        // 将种子混入数据，产生独立哈希
        byte[] seeded = new byte[data.length + Long.BYTES];
        System.arraycopy(data, 0, seeded, 0, data.length);
        for (int i = 0; i < Long.BYTES; i++) {
            seeded[data.length + i] = (byte) (seed >>> (i * 8));
        }
        return HashUtil.mmh3(seeded) & Integer.MAX_VALUE;
    }
}
