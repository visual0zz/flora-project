package com.flora.mock.jsonschema.impl;

import com.flora.mock.jsonschema.GenerationConfig;

/**
 * 生成上下文：随机源、配置、当前深度、长度预算。
 * <p>budget 为当前节点可消耗的推荐长度配额，随递归逐层分配；
 * {@link #deeper()} 保留继承预算，需要显式分配时用 {@link #deeper(int)}。</p>
 */
public final class GenerationContext {

    private final RandomSupport random;
    private final GenerationConfig config;
    private final int depth;
    private final int budget;

    public GenerationContext(RandomSupport random, GenerationConfig config) {
        this(random, config, 0, config.targetLength());
    }

    private GenerationContext(RandomSupport random, GenerationConfig config, int depth, int budget) {
        this.random = random;
        this.config = config;
        this.depth = depth;
        this.budget = budget;
    }

    GenerationContext deeper() {
        return new GenerationContext(random, config, depth + 1, budget);
    }

    /** 下探一层并携带子节点的长度预算。 */
    GenerationContext deeper(int childBudget) {
        return new GenerationContext(random, config, depth + 1, childBudget);
    }

    /** 不改变深度，仅调整当前长度预算。 */
    GenerationContext withBudget(int newBudget) {
        return new GenerationContext(random, config, depth, newBudget);
    }

    RandomSupport random() {
        return random;
    }

    GenerationConfig config() {
        return config;
    }

    int depth() {
        return depth;
    }

    int budget() {
        return budget;
    }
}
