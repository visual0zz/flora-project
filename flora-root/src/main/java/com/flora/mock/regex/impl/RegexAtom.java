package com.flora.mock.regex.impl;

/**
 * 正则原子：模式中的一个可重复单元。
 * <p>原子承担两件事：{@link #estimate()} 给出单次生成的期望长度（供预算分配），
 * {@link #append(StringBuilder)} 把一次或多次生成结果写入输出。</p>
 */
public interface RegexAtom {

    void append(StringBuilder sb);

    /** 单次 emit 的期望长度。 */
    int estimate();

    /** 按量词下界的最小总长。 */
    int minTotal();

    /** 按量词上界的最大总长（无界按 MAX_REPEAT 折算）。 */
    int maxTotal();
}
