package com.flora.entropy.probds;

import com.flora.entropy.hash.MurmurHash;
import com.flora.tag.ThreadFragile;

import java.nio.charset.StandardCharsets;

/**
 * HyperLogLog（HLL）——一种基数估计算法（distinct count estimation）。
 * <p>
 * 使用固定内存（2^p 字节）估计数据流中不重复元素的数量，
 * 标准误差约为 {@code 1.04 / sqrt(2^p)}。
 * 适用于大数据量下的 UV 统计、去重计数等场景。
 * </p>
 * <p>
 * 实现基于 Flajolet 等人的 HyperLogLog 论文，包含：
 * <ul>
 *   <li>小范围线性计数（registers &lt; 2.5m 时）</li>
 *   <li>大范围 64 位修正（原始 HLL 在接近 2^32 时溢出）</li>
 * </ul>
 * 哈希使用 {@link MurmurHash#mmh3(byte[])} 产生 32 位结果，
 * 通过两个独立调用拼接为 64 位哈希，以支持精度 p 高达 16。
 * </p>
 */
@ThreadFragile
public final class HyperLogLog {

    private final int p;            // 精度位数（寄存器寻址）
    private final int m;            // 寄存器数量 = 2^p
    private final byte[] registers; // 每个寄存器存储前导零数 + 1
    private boolean dirty;          // 缓存失效标记
    private double cache;           // 缓存估计值

    /**
     * 构造 HyperLogLog。
     *
     * @param p 精度（4 ~ 16）。每增加 1，误差减半，内存翻倍。
     *          常用值：p=14 (~1.6KB, 误差 ~0.65%)，p=10 (~128B, 误差 ~3.25%)
     */
    public HyperLogLog(int p) {
        if (p < 4 || p > 16) {
            throw new IllegalArgumentException("p 必须在 4 ~ 16 之间，当前: " + p);
        }
        this.p = p;
        this.m = 1 << p;
        this.registers = new byte[m];
        this.dirty = true;
    }

    /**
     * 向 HyperLogLog 添加一个元素。
     *
     * @param data 输入数据（字节数组）
     */
    public void add(byte[] data) {
        // 获取 64 位哈希：用两次 MurmurHash3 32 位，1次 mmh3+1次mmh3(翻转)
        long hash = hash64(data);
        // 取前 p 位作为寄存器索引
        int idx = (int) (hash >>> (Long.SIZE - p));
        // 剩余位：移除前 p 位，计算前导零数 + 1
        int w = (int) ((hash << p) >>> (Long.SIZE - Integer.SIZE));
        int leading = numberOfLeadingZeros(w) + 1;
        if (leading > registers[idx]) {
            registers[idx] = (byte) leading;
            dirty = true;
        }
    }

    /**
     * 向 HyperLogLog 添加一个字符串元素（UTF-8 编码）。
     *
     * @param s 输入字符串
     */
    public void add(String s) {
        add(s == null ? new byte[0] : s.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 向 HyperLogLog 添加一个 int 元素。
     *
     * @param n 输入整数值
     */
    public void add(int n) {
        add(new byte[]{
                (byte) (n >>> 24),
                (byte) (n >>> 16),
                (byte) (n >>> 8),
                (byte) n
        });
    }

    /**
     * 返回基数估计值（去重元素数量的近似值）。
     *
     * @return 估计值
     */
    public long estimate() {
        if (!dirty) {
            return (long) cache;
        }
        double sum = 0;
        int zeroCount = 0;
        for (byte r : registers) {
            sum += 1.0 / (1 << r);
            if (r == 0) {
                zeroCount++;
            }
        }
        double alpha = alpha(p);
        double raw = alpha * m * m / sum;

        // 小范围修正：线性计数
        if (raw < 2.5 * m && zeroCount > 0) {
            raw = linearCounting(m, zeroCount);
        }
        // 大范围基数由 64 位哈希自然支持，无需额外的修正步骤
        long result = (long) Math.ceil(raw);
        cache = result;
        dirty = false;
        return result;
    }

    /** 将两个 HyperLogLog 实例合并（用于分布式基数计算）。 */
    public void merge(HyperLogLog other) {
        if (this.p != other.p) {
            throw new IllegalArgumentException("精度不一致，无法合并");
        }
        boolean changed = false;
        for (int i = 0; i < m; i++) {
            if (other.registers[i] > this.registers[i]) {
                this.registers[i] = other.registers[i];
                changed = true;
            }
        }
        if (changed) {
            dirty = true;
        }
    }

    /** 返回精度值 p。 */
    public int precision() { return p; }

    /** 返回寄存器数量。 */
    public int registerCount() { return m; }

    /** 返回占用内存（字节）。 */
    public int memoryBytes() { return registers.length; }

    // ==================== 内部工具 ====================

    /** 将 32 位 mmh3 扩展为 64 位哈希。 */
    private static long hash64(byte[] data) {
        int low = MurmurHash.mmh3(data);
        // 翻转部分字节获得第二个独立 32 位
        byte[] flipped = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            flipped[i] = (byte) ~data[i];
        }
        int high = MurmurHash.mmh3(flipped);
        return ((long) high << 32) | (low & 0xFFFFFFFFL);
    }

    /** 计算 32 位 int 的前导零数（包含符号位）。 */
    private static int numberOfLeadingZeros(int x) {
        if (x == 0) return 32;
        int n = 0;
        if ((x & 0xFFFF0000) == 0) { n += 16; x <<= 16; }
        if ((x & 0xFF000000) == 0) { n += 8;  x <<= 8;  }
        if ((x & 0xF0000000) == 0) { n += 4;  x <<= 4;  }
        if ((x & 0xC0000000) == 0) { n += 2;  x <<= 2;  }
        if ((x & 0x80000000) == 0) { n += 1;           }
        return n;
    }

    /** 返回 alpha 常量。 */
    private static double alpha(int p) {
        switch (p) {
            case 4:  return 0.673;
            case 5:  return 0.697;
            case 6:  return 0.709;
            default: return 0.7213 / (1 + 1.079 / (1 << p));
        }
    }

    /** 线性计数修正。 */
    private static double linearCounting(int m, int zeroCount) {
        return m * Math.log((double) m / zeroCount);
    }
}
