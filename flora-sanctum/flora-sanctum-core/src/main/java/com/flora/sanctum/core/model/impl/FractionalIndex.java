package com.flora.sanctum.core.model.impl;

import com.flora.root.codec.json.model.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 小数索引（fractional indexing）：以 {@code double} 作排序键，使节点可在任意两邻居之间插入，
 * 而不必重排整段。被移动/插入的节点只改写自身 {@code order} 为相邻中点，把数据修改范围压到最小
 * （日常插入仅改一块；只有间隙耗尽无法再取中点时才 {@link #rebalance} 整段重排）。
 *
 * <p>参数（见方案 idea）：{@code X=32}（同一空隙最多插 32 次触发重排）、
 * {@code D=2^33}（重排默认间隔）、{@code L=2}（重排阈值）。其中 D/L 的相对比值决定容量与重排频率的
 * trade-off，绝对值无所谓：{@code N = 2^(52-X) ≈ 2^20}（单列表容量约百万）。</p>
 *
 * <p>排序键以 64 位 bits 形式落盘（{@code orderBits}，见 {@link TreeContext}），此处只做纯数学，
 * 不依赖持久化细节，便于独立单测。</p>
 */
public final class FractionalIndex {

    /** 重排默认间隔 D = 2^33。 */
    public static final double D = 8_589_934_592.0;
    /** 重排阈值 L：相邻 order 间隙小于该值且无可表示中点时触发全局重排。 */
    public static final double L = 2.0;
    /** 最坏重排次数 X = log2(D/L) = 32。 */
    public static final int X = 32;

    private FractionalIndex() {
    }

    /** 追加到末尾：maxSiblingOrder + D（无兄弟时从 D 起）。 */
    public static double initialOrder(double maxSiblingOrder) {
        return maxSiblingOrder + D;
    }

    /**
     * 取 before 与 after 之间的中点作为新 order。
     * @param before 前驱 order；插入到「首位之前」时传 0（即 0 与首位中点 = after/2）
     * @param after  后继 order；为 null 表示插到末尾（before + D）
     */
    public static double between(double before, Double after) {
        if (after == null) {
            return before + D;
        }
        return (before + after) / 2.0;
    }

    /** 间隙是否已无可表示中点（ulp 耗尽），需触发重排。 */
    public static boolean collapsed(double before, double after) {
        double mid = (before + after) / 2.0;
        return mid == before || mid == after;
    }

    /**
     * 对 parent 下 sibling 列表按当前 order 重排，赋 order = (i+1)*D 并落盘。
     * 仅此处会发生多块改写；日常插入只改被移动节点一块。
     */
    public static void rebalance(TreeContext ctx, UUID parent, List<UUID> siblings) {
        List<UUID> sorted = new ArrayList<>(siblings);
        sorted.sort((a, b) -> Double.compare(ctx.orderOf(a), ctx.orderOf(b)));
        for (int i = 0; i < sorted.size(); i++) {
            UUID u = sorted.get(i);
            JsonObject obj = ctx.read(u);
            if (obj == null) {
                continue;
            }
            obj.put("orderBits", Double.doubleToLongBits((i + 1) * D));
            ctx.write(u, obj, parent);
        }
    }
}
