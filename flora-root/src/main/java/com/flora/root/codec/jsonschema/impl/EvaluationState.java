package com.flora.root.codec.jsonschema.impl;

import java.util.HashSet;
import java.util.Set;

/**
 * 求值状态（2020-12 unevaluatedProperties/unevaluatedItems 支持）。
 * <p>记录在当前校验作用域内已被求值的对象属性名与数组索引。
 * 支持快照/合并，供 allOf/anyOf/oneOf/if-then-else 的分支求值传播使用。</p>
 */
public final class EvaluationState {

    private final Set<String> evaluatedProperties;
    private final Set<Integer> evaluatedIndices;

    public EvaluationState() {
        this(new HashSet<>(), new HashSet<>());
    }

    private EvaluationState(Set<String> properties, Set<Integer> indices) {
        this.evaluatedProperties = properties;
        this.evaluatedIndices = indices;
    }

    public void evaluateProperty(String name) {
        evaluatedProperties.add(name);
    }

    public void evaluateIndex(int index) {
        evaluatedIndices.add(index);
    }

    public void merge(EvaluationState other) {
        evaluatedProperties.addAll(other.evaluatedProperties);
        evaluatedIndices.addAll(other.evaluatedIndices);
    }

    public boolean isPropertyEvaluated(String name) {
        return evaluatedProperties.contains(name);
    }

    public boolean isIndexEvaluated(int index) {
        return evaluatedIndices.contains(index);
    }

    public EvaluationState snapshot() {
        return new EvaluationState(new HashSet<>(evaluatedProperties), new HashSet<>(evaluatedIndices));
    }
}
