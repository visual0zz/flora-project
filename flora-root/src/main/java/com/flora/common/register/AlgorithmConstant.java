package com.flora.common.register;

/**
 * 常量型组件：组合算法构造时直接给定的标量取值（如输出长度、轮数、预设 IV 等）。
 * <p>区别于 {@link Algorithm}：本类型承载的是「字面量值」，不参与算法的递归解析。
 * 取值经 {@link #getValue()} 取出，其声明类型由 {@link #getType()} 自述，供构造期类型校验。</p>
 *
 * @param <T> 常量值的类型（通常是 {@code Integer} / {@code byte[]} / {@code String} 等）
 */
public interface AlgorithmConstant<T> extends AlgorithmComponent {
    /** @return 被注入的常量值 */
    T getValue();

    /** @return 常量值的声明类型 */
    Class<T> getType();
}
