package com.flora.root.mock.jsonschema.impl;

import java.util.IdentityHashMap;

/**
 * 生成上下文：随机源、推荐长度、当前深度、长度预算、递归路径。
 * <p>budget 为当前节点可消耗的推荐长度配额，随递归逐层分配；
 * {@link #deeper()} 保留继承预算，需要显式分配时用 {@link #deeper(int)}。</p>
 * <p>path 记录当前展开路径上的 schema 对象（按 identity），用于检测
 * {@code $ref} 循环引用；递归展开时 enterPath/exitPath 配对维护。</p>
 */
public final class GenerationContext {

    private final RandomSupport random;
    private final int targetLength;
    private final int depth;
    private final int budget;
    private final IdentityHashMap<Object, Boolean> path;

    public GenerationContext(RandomSupport random, int targetLength) {
        this(random, targetLength, 0, targetLength, new IdentityHashMap<>());
    }

    private GenerationContext(RandomSupport random, int targetLength, int depth, int budget,
                              IdentityHashMap<Object, Boolean> path) {
        this.random = random;
        this.targetLength = targetLength;
        this.depth = depth;
        this.budget = budget;
        this.path = path;
    }

    GenerationContext deeper() {
        return new GenerationContext(random, targetLength, depth + 1, budget, path);
    }

    /** 下探一层并携带子节点的长度预算。 */
    GenerationContext deeper(int childBudget) {
        return new GenerationContext(random, targetLength, depth + 1, childBudget, path);
    }

    /** 不改变深度，仅调整当前长度预算。 */
    GenerationContext withBudget(int newBudget) {
        return new GenerationContext(random, targetLength, depth, newBudget, path);
    }

    RandomSupport random() {
        return random;
    }

    int targetLength() {
        return targetLength;
    }

    int depth() {
        return depth;
    }

    int budget() {
        return budget;
    }

    boolean onPath(Object schema) {
        return schema != null && path.containsKey(schema);
    }

    void enterPath(Object schema) {
        if (schema != null) {
            path.put(schema, Boolean.TRUE);
        }
    }

    void exitPath(Object schema) {
        if (schema != null) {
            path.remove(schema);
        }
    }
}
