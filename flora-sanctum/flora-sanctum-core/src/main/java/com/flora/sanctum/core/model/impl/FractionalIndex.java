package com.flora.sanctum.core.model.impl;

/**
 * 小数索引（fractional indexing）：以 {@code long} 作排序键，使节点可在任意两邻居之间插入，
 * 而不必重排整段。被移动/插入的节点只改写自身 {@code order} 为相邻中点，把数据修改范围压到最小
 * （日常插入仅改一块；只有间隙耗尽或追加将溢出时才整段重排）。
 *
 * <p>参数：{@code X=32}（同一空隙最多插 32 次触发重排）、{@code D=2^33}（重排默认间隔）、
 * {@code L=2}（最小可用间隙）。D/L 的比值决定容量与重排频率的 trade-off，单列表容量约
 * {@code 2^63/D = 2^30}。</p>
 *
 * <p>刻意选用 {@code long} 而非 {@code double}：long 分辨率恒为 1、不随量级退化
 * （double 的 {@code ulp(x)} 随 x 增长，会把容量压到 {@code 2^(52-X)}），且往返 JSON 天然精确、
 * 无浮点边界情况。</p>
 *
 * <p><b>L 保持 2 而非数学上最小的 1</b>：保证 {@code (b-a)/2 >= 1}，新 order 严格落在两邻居之间；
 * 间隙为 1 时中点会退化成 {@code a}，造成顺序冲突。</p>
 *
 * <p><b>头部插入</b>：以 0 作为虚拟下界，插到首位时取 {@code between(0, firstOrder)} = {@code firstOrder/2}。
 * 0 不会被真实节点占用（重排与缺省赋序都从 D 起，真实 order 恒 {@code >= D}），且 {@code collapsed}
 * 在间隙 {@code < L} 时就先重排，故中点永不退化为 0 或任一邻居。头部连续插入 32 次后间隙缩到 {@code < 2}
 * 触发重排，与 X=32 一致。</p>
 *
 * <p>本类只做纯数学，不依赖持久化细节，便于独立单测。</p>
 */
public final class FractionalIndex {

    /** 重排默认间隔 D = 2^33。 */
    public static final long D = 8_589_934_592L;
    /** 最小可用间隙 L=2：保证 (b-a)/2 >= 1，中点严格居中而不退化。 */
    public static final long L = 2L;
    /** 最坏重排次数 X = log2(D/L) = 32。 */
    public static final int X = 32;

    private FractionalIndex() {
    }

    /** 追加到末尾：maxSiblingOrder + D（调用方须先用 {@link #appendOverflow} 判溢出）。 */
    public static long initialOrder(long maxSiblingOrder) {
        return maxSiblingOrder + D;
    }

    /** 追加到末尾是否会溢出 long（此时应先触发全局重排）。 */
    public static boolean appendOverflow(long lastOrder) {
        return lastOrder > Long.MAX_VALUE - D;
    }

    /**
     * 取 before 与 after 之间的中点作为新 order。
     * <p>用 {@code before + (after - before) / 2} 而非 {@code (before + after) / 2}：
     * 后者在两者都接近 {@code Long.MAX_VALUE} 时 {@code before + after} 会溢出成负数。</p>
     *
     * @param before 前驱 order；插入到「首位之前」时传 0（即 0 与首位中点 = after/2）
     * @param after  后继 order；为 null 表示插到末尾（before + D）
     */
    public static long between(long before, Long after) {
        if (after == null) {
            return before + D;
        }
        return before + (after - before) / 2;
    }

    /** 间隙是否已不足以容纳中点（{@code < L} 时 (b-a)/2 会退化成 0），需触发重排。 */
    public static boolean collapsed(long before, long after) {
        return after - before < L;
    }
}
