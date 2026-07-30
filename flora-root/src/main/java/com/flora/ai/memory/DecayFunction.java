package com.flora.ai.memory;

/**
 * 记忆衰减函数。
 * <p>纯数学：根据时间流逝计算新鲜度分数。</p>
 */
public class DecayFunction {

    private final long halfLifeMs;

    public DecayFunction(long halfLifeMs) {
        this.halfLifeMs = halfLifeMs;
    }

    /** 默认半衰期 24 小时。 */
    public static DecayFunction withDefaultHalfLife() {
        return new DecayFunction(24 * 60 * 60 * 1000L);
    }

    /** 指数衰减：score = e^(-ln(2) * elapsed / halfLife)。 */
    public double apply(long timestamp) {
        long elapsed = System.currentTimeMillis() - timestamp;
        if (elapsed <= 0) return 1.0;
        return Math.exp(-Math.log(2) * elapsed / halfLifeMs);
    }
}
