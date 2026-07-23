package com.flora.java;

/**
 * 目标类型匹配器，用于判断转换器是否支持指定的目标类型和元素类型。
 * <p>
 * 这是一个函数式接口，可通过 lambda 表达式实现。
 * </p>
 */
@FunctionalInterface
public interface TargetMatcher {

    /**
     * 判断是否匹配指定的目标类型和元素类型。
     *
     * @param targetType  目标类型
     * @param elementType 元素类型（用于集合/数组类型的转换），可为 null
     * @return 如果匹配则返回 true
     */
    boolean matches(Class<?> targetType, Class<?> elementType);
}
