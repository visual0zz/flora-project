package com.flora.root.container.order;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 小数索引的 {@code long} 实现：以 64 位整数作键，在 {@code a} 与 {@code b} 之间取中点
 * （{@code a + (b - a) / 2}，并做防溢出处理）。
 *
 * <p><b>键域</b>：由构造参数 {@code positiveOnly} 决定。关闭（默认）时域为整个 {@code long} 范围，
 * 最前哨兵 {@link Long#MIN_VALUE}、最后哨兵 {@link Long#MAX_VALUE}，{@link #first()} 落在 {@code 0}
 * 附近；开启时域为 {@code [0, Long.MAX_VALUE]}（纯正数），内部以 {@code -1} 作最前哨兵，
 * {@link #first()} 落在正数域中点，且 {@link #isValid} 拒绝负数。</p>
 *
 * <p><b>与字符串版的根本区别</b>：{@code long} 是定宽 64 位，可插入的间隙有限。当两个键相邻
 * （相差 1）时不再存在严格介于其间的整数，{@link #between} 会抛出 {@link IllegalStateException}
 * ——此时调用方必须对整个序列做重排（reindex）以腾出空间。定宽键没有可供随机抖动的精度，
 * 并发唯一性需由外部协调或重排保证。</p>
 *
 * <p><b>适用场景</b>：元素数量与插入频率都较低、能容忍偶尔重排的列表。若需要「永不重排」的无限
 * 插入，请使用字符串版 {@link RocicorpFractionalIndex}。</p>
 *
 * <p>本类无状态，线程安全；实现 {@link FractionalIndex}{@code <Long>}，通过 {@link #INSTANCE}
 * （全范围）或 {@link #POSITIVE}（纯正数域）以多态方式使用。</p>
 */
public final class LongFractionalIndex implements FractionalIndex<Long> {

    /** 全范围实例：键域为整个 {@code long}，允许负数（默认语义）。 */
    public static final LongFractionalIndex INSTANCE = new LongFractionalIndex(false);

    /** 纯正数域实例：键域为 {@code [0, Long.MAX_VALUE]}，{@link #isValid} 拒绝负数。 */
    public static final LongFractionalIndex POSITIVE = new LongFractionalIndex(true);

    private static final long MIN = Long.MIN_VALUE;
    private static final long MAX = Long.MAX_VALUE;
    /** 纯正数域的最前哨兵（不在合法键域内）。 */
    private static final long FRONT = -1L;

    /** 键域是否限制为纯正数（{@code [0, Long.MAX_VALUE]}）。 */
    private final boolean positiveOnly;

    public LongFractionalIndex(boolean positiveOnly) {
        this.positiveOnly = positiveOnly;
    }

    // ====== 接口实现 ======

    @Override
    public Long first() {
        return between(null, null);
    }

    @Override
    public Long between(Long a, Long b) {
        long lo = a == null ? (positiveOnly ? FRONT : MIN) : a;
        long hi = b == null ? MAX : b;
        if (lo >= hi) {
            throw new IllegalArgumentException("a 必须小于 b: " + a + " / " + b);
        }
        if (hi == lo + 1) {
            throw new IllegalStateException(
                    "间隙不足，无法在 " + a + " 与 " + b + " 之间插入，需对序列整体重排（reindex）");
        }
        long mid = midpoint(lo, hi);
        if (positiveOnly && mid < 0) {
            throw new IllegalStateException(
                    "纯正数域下无可用中间键（介于 " + a + " 与 " + b + " 之间），需对序列整体重排（reindex）");
        }
        return mid;
    }

    @Override
    public List<Long> nBetween(Long a, Long b, int n) {
        if (n < 0) {
            throw new IllegalArgumentException("n 不能为负: " + n);
        }
        if (n == 0) {
            return List.of();
        }
        if (n == 1) {
            return List.of(between(a, b));
        }
        if (b == null) {
            List<Long> out = new ArrayList<>(n);
            Long c = between(a, null);
            out.add(c);
            for (int i = 1; i < n; i++) {
                c = between(c, null);
                out.add(c);
            }
            return List.copyOf(out);
        }
        if (a == null) {
            List<Long> out = new ArrayList<>(n);
            Long c = between(null, b);
            out.add(c);
            for (int i = 1; i < n; i++) {
                c = between(null, c);
                out.add(c);
            }
            Collections.reverse(out);
            return List.copyOf(out);
        }
        int mid = n / 2;
        Long c = between(a, b);
        List<Long> out = new ArrayList<>(n);
        out.addAll(nBetween(a, c, mid));
        out.add(c);
        out.addAll(nBetween(c, b, n - mid - 1));
        return List.copyOf(out);
    }

    @Override
    public int compare(Long a, Long b) {
        if (a == null) {
            return b == null ? 0 : -1;
        }
        if (b == null) {
            return 1;
        }
        return Long.compare(a, b);
    }

    @Override
    public boolean isValid(Long key) {
        if (key == null) {
            return false;
        }
        return positiveOnly ? key >= 0L : true;
    }

    /** 防溢出的中点：异号用 {@code (lo + hi) / 2}，同号用 {@code lo + (hi - lo) / 2}。 */
    private static long midpoint(long lo, long hi) {
        if ((lo < 0) != (hi < 0)) {
            return (lo + hi) / 2;
        }
        return lo + (hi - lo) / 2;
    }
}
