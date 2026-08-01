package com.flora.mock.regex.impl;

import java.util.List;

/**
 * 两阶段弹性长度分配：把目标长度配额按原子权重分配到各原子。
 * <p>先统计各原子最小总长 totalMin：target 不足则全部按最小值生成（硬约束优先）；
 * 超出则按原子单次长度（estimate）为权重瓜分剩余配额，分配次数 clamp 在量词区间内。</p>
 */
public final class RegexAllocator {

    private RegexAllocator() {
    }

    public static void allocate(List<RegexAtom> atoms, int target) {
        long totalMin = 0;
        for (RegexAtom a : atoms) {
            totalMin += a.minTotal();
        }
        if (target <= totalMin) {
            for (RegexAtom a : atoms) {
                BaseAtom ba = (BaseAtom) a;
                ba.forceCount(ba.min());
            }
            return;
        }
        long extra = target - totalMin;
        long totalWeight = 0;
        for (RegexAtom a : atoms) {
            totalWeight += a.estimate();
        }
        for (RegexAtom a : atoms) {
            BaseAtom ba = (BaseAtom) a;
            // 先按长度权重算出目标总长，再折算成次数（总长 / 单次长度）
            long share = totalWeight == 0 ? 0 : extra * ba.estimate() / totalWeight;
            int targetLen = ba.min() * ba.estimate() + (int) share;
            int count = Math.max(ba.min(), targetLen / Math.max(1, ba.estimate()));
            count = Math.min(count, ba.maxTotalCount());
            ba.forceCount(count);
        }
    }
}
