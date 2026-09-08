package com.flora.root.mock.jsonschema.impl;

import java.util.IdentityHashMap;

/**
 * 生成上下文：随机源、当前深度、当前属性名、递归路径。
 * <p><b>展开决策不再依赖长度预算</b>：可选部分（可选属性、额外属性、递归引用）
 * 一律由 {@link #expandProbability()} 给出的概率决定——该概率随深度指数递减，
 * 越深越难继续展开，结构天然收敛；必填部分（{@code required}/{@code minItems} 等）不受影响。</p>
 * <p>{@code name} 为当前属性名，供字符串字段按名猜测语义（见
 * {@link SemanticStringGenerator}）；递归下探时由调用方逐层传入。</p>
 * <p>{@code path} 记录当前展开路径上的 schema 对象（按 identity），用于检测
 * {@code $ref} 循环引用；递归展开时 enterPath/exitPath 配对维护。</p>
 */
public final class GenerationContext {

    /** 深度 0 时继续展开的基础概率。 */
    private static final double EXPAND_BASE = 0.9;
    /** 每深一层的衰减因子。 */
    private static final double EXPAND_DECAY = 0.7;
    /** 概率下限：极深处仍保留小概率继续展开，避免深层被一刀切断。 */
    private static final double EXPAND_FLOOR = 0.02;

    private final RandomSupport random;
    private final int depth;
    private final String name;
    private final IdentityHashMap<Object, Boolean> path;

    public GenerationContext(RandomSupport random) {
        this(random, 0, "", new IdentityHashMap<>());
    }

    private GenerationContext(RandomSupport random, int depth, String name,
                              IdentityHashMap<Object, Boolean> path) {
        this.random = random;
        this.depth = depth;
        this.name = name;
        this.path = path;
    }

    /** 下探一层，沿用当前属性名。 */
    GenerationContext deeper() {
        return deeper(name);
    }

    /** 下探一层，并携带子节点的属性名（供语义推断）。 */
    GenerationContext deeper(String childName) {
        return new GenerationContext(random, depth + 1, childName == null ? "" : childName, path);
    }

    /** 当前属性名（语义推断用；顶层为空串）。 */
    String name() {
        return name;
    }

    int depth() {
        return depth;
    }

    /** 继续展开一层的概率：随深度指数递减，不低于 {@link #EXPAND_FLOOR}。 */
    double expandProbability() {
        return Math.max(EXPAND_FLOOR, EXPAND_BASE * Math.pow(EXPAND_DECAY, depth));
    }

    /** 是否继续展开一层（可选结构的取舍，已按本层概率抽样）。 */
    boolean shouldExpand() {
        return random.nextDouble() < expandProbability();
    }

    RandomSupport random() {
        return random;
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
