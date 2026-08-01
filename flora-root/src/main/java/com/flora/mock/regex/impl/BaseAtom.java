package com.flora.mock.regex.impl;

import java.util.random.RandomGenerator;

/**
 * 原子基类：持有量词区间与随机源，按区间次数重复 emit。
 */
public abstract class BaseAtom implements RegexAtom {

    /** 单次重复数量的上限，超出视为不支持。 */
    public static final int MAX_REPEAT = 256;

    private final RandomGenerator random;
    private int min = 1; // 无量词时恰好生成一次
    private int max = 1;
    private int forcedCount = -1; // 预算分配后的目标次数；-1 表示未分配

    protected BaseAtom(RandomGenerator random) {
        this.random = random;
    }

    public void quantifier(int min, int max) {
        this.min = min;
        this.max = max;
    }

    public int min() {
        return min;
    }

    public int max() {
        return max;
    }

    public int maxTotalCount() {
        return max >= 0 ? max : MAX_REPEAT;
    }

    /** 预算分配时固定生成次数；-1 恢复随机。 */
    public void forceCount(int count) {
        this.forcedCount = count;
    }

    @Override
    public void append(StringBuilder sb) {
        int count = forcedCount >= 0 ? forcedCount : count();
        for (int i = 0; i < count; i++) {
            emit(sb);
        }
    }

    private int count() {
        if (max < 0) {
            return min + randomOf(0, Math.min(3, MAX_REPEAT - min)); // {n,} 上限放宽
        }
        if (max == min) {
            return min;
        }
        return min + randomOf(0, max - min);
    }

    @Override
    public int minTotal() {
        return min * estimate();
    }

    @Override
    public int maxTotal() {
        return maxTotalCount() * estimate();
    }

    protected abstract void emit(StringBuilder sb);

    protected int randomOf(int lo, int hi) {
        if (hi <= lo) {
            return lo;
        }
        return lo + random.nextInt(hi - lo + 1);
    }

    protected RandomGenerator random() {
        return random;
    }
}
