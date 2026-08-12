package com.flora.root.mock.regex.automaton;

import java.util.ArrayList;
import java.util.List;

/**
 * 字符集合：排序的码点区间列表，支持并/交/补代数运算。
 * <p>表示：{@code int[] ranges}，偶数下标=区间起点、奇数下标=区间终点（含端点），
 * 升序且不重叠。全集限定 BMP（0x0000..0xFFFF，排除代理区 0xD800..0xDFFF）。</p>
 */
public final class CharSet {

    /** 空集合。 */
    public static final CharSet EMPTY = new CharSet(new int[0]);

    /** BMP 全集（排除代理区）。 */
    public static final CharSet ALL = complement(EMPTY);

    private final int[] ranges;

    private CharSet(int[] ranges) {
        this.ranges = ranges;
    }

    /** 从区间数组构建（假定已排序不重叠；内部按需规范化）。 */
    public static CharSet of(int[] ranges) {
        return new CharSet(normalize(ranges));
    }

    /** 单个字符。 */
    public static CharSet ofChar(int cp) {
        return of(new int[]{cp, cp});
    }

    /** 连续区间 [lo, hi]（含端点）。 */
    public static CharSet ofRange(int lo, int hi) {
        return of(new int[]{lo, hi});
    }

    /** 并集：合并两集合区间。 */
    public static CharSet union(CharSet a, CharSet b) {
        return of(merge(a.ranges, b.ranges));
    }

    /** 交集：双指针取重叠区间。 */
    public static CharSet intersect(CharSet a, CharSet b) {
        List<int[]> out = new ArrayList<>();
        int i = 0, j = 0;
        while (i < a.ranges.length && j < b.ranges.length) {
            int lo = Math.max(a.ranges[i], b.ranges[j]);
            int hi = Math.min(a.ranges[i + 1], b.ranges[j + 1]);
            if (lo <= hi) {
                out.add(new int[]{lo, hi});
            }
            if (a.ranges[i + 1] < b.ranges[j + 1]) {
                i += 2;
            } else {
                j += 2;
            }
        }
        return of(flatten(out));
    }

    /** 补集：对 BMP 全集取补。 */
    public static CharSet complement(CharSet a) {
        List<int[]> out = new ArrayList<>();
        int cursor = 0;
        for (int i = 0; i < a.ranges.length; i += 2) {
            int lo = a.ranges[i];
            int hi = a.ranges[i + 1];
            if (lo > cursor) {
                addRange(out, cursor, lo - 1);
            }
            cursor = hi + 1;
        }
        if (cursor <= 0xFFFF) {
            addRange(out, cursor, 0xFFFF);
        }
        return of(flatten(out));
    }

    /** 是否非空。 */
    public boolean isEmpty() {
        return ranges.length == 0;
    }

    /** 是否包含指定码点。 */
    public boolean contains(int cp) {
        int i = 0;
        while (i < ranges.length && ranges[i + 1] < cp) {
            i += 2;
        }
        return i < ranges.length && ranges[i] <= cp && cp <= ranges[i + 1];
    }

    /** 区间数组（调用方不得修改）。 */
    int[] ranges() {
        return ranges;
    }

    /** 总字符数（用于加权采样）。 */
    long size() {
        long total = 0;
        for (int i = 0; i < ranges.length; i += 2) {
            total += (long) ranges[i + 1] - ranges[i] + 1;
        }
        return total;
    }

    /** 按区间长度加权取第 index 个码点（index ∈ [0, size)）。 */
    int codePointAt(long index) {
        for (int i = 0; i < ranges.length; i += 2) {
            long len = (long) ranges[i + 1] - ranges[i] + 1;
            if (index < len) {
                return ranges[i] + (int) index;
            }
            index -= len;
        }
        throw new IndexOutOfBoundsException("index 超出 CharSet");
    }

    // ── 工具 ──

    private static int[] merge(int[] a, int[] b) {
        List<int[]> out = new ArrayList<>();
        int i = 0, j = 0;
        int curLo = -1, curHi = -1;
        while (i < a.length || j < b.length) {
            int lo, hi;
            if (j >= b.length || (i < a.length && a[i] < b[j])) {
                lo = a[i];
                hi = a[i + 1];
                i += 2;
            } else {
                lo = b[j];
                hi = b[j + 1];
                j += 2;
            }
            if (curLo < 0) {
                curLo = lo;
                curHi = hi;
            } else if (lo <= curHi + 1) {
                curHi = Math.max(curHi, hi);
            } else {
                out.add(new int[]{curLo, curHi});
                curLo = lo;
                curHi = hi;
            }
        }
        if (curLo >= 0) {
            out.add(new int[]{curLo, curHi});
        }
        return flatten(out);
    }

    private static int[] normalize(int[] in) {
        if (in.length <= 2) {
            return in;
        }
        // 排序 + 合并
        Integer[] boxed = new Integer[in.length];
        for (int i = 0; i < in.length; i++) {
            boxed[i] = in[i];
        }
        java.util.Arrays.sort(boxed);
        List<int[]> out = new ArrayList<>();
        int curLo = boxed[0], curHi = boxed[1];
        for (int i = 2; i < in.length; i += 2) {
            int lo = boxed[i], hi = boxed[i + 1];
            if (lo <= curHi + 1) {
                curHi = Math.max(curHi, hi);
            } else {
                out.add(new int[]{curLo, curHi});
                curLo = lo;
                curHi = hi;
            }
        }
        out.add(new int[]{curLo, curHi});
        return flatten(out);
    }

    private static void addRange(List<int[]> out, int lo, int hi) {
        if (lo <= hi) {
            out.add(new int[]{lo, hi});
        }
    }

    private static int[] flatten(List<int[]> list) {
        int[] flat = new int[list.size() * 2];
        for (int i = 0; i < list.size(); i++) {
            flat[i * 2] = list.get(i)[0];
            flat[i * 2 + 1] = list.get(i)[1];
        }
        return flat;
    }
}
