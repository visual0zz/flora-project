package com.flora.root.container.order;

import java.util.List;

/**
 * 小数索引（fractional indexing）的抽象：在任意两个有序键 {@code a}、{@code b} 之间生成一个
 * 严格介于其间的新键，从而支持「无限插入、无需重排序列」的列表排序。
 *
 * <p>本接口不规定键的表示形式——{@code T} 可以是字符串（如 {@link RocicorpFractionalIndex}，
 * 可无限细分、键长缓慢增长），也可以是定宽整数（如 {@link LongFractionalIndex}，超出精度后
 * 需整体重排）。使用方应只依赖本接口的契约，不依赖具体实现。</p>
 *
 * <p><b>契约</b>：所有实现必须满足
 * <ul>
 *   <li>{@link #compare(Object, Object)} 给出严格全序，且 {@code null} 视为最小（排在最前）；</li>
 *   <li>对不相邻的 {@code a < b}，{@link #between(Object, Object)} 的结果 {@code c} 严格介于其间，
 *       即 {@code compare(a, c) < 0 < compare(c, b)}；</li>
 *   <li>{@link #first()} 等价于 {@code between(null, null)}。</li>
 * </ul>
 * 排序语义由各实现决定，但必须保证上述前后关系可被 {@link #compare} 复现（例如字符串版按字典序、
 * Long 版按数值序）。</p>
 *
 * @param <T> 键的类型
 */
public interface FractionalIndex<T> {

    /** 生成一个初始键（等价于 {@code between(null, null)}）。 */
    T first();

    /**
     * 生成严格位于 {@code a} 与 {@code b} 之间的键。
     *
     * @param a 下界；{@code null} 表示插到最前
     * @param b 上界；{@code null} 表示追加到末尾
     * @throws IllegalArgumentException 两界均非 {@code null} 且 {@code a >= b}
     */
    T between(T a, T b);

    /**
     * 同 {@link #between}，但允许实现在并发插入场景下引入随机性以分散键空间。
     * 不支持该语义的实现（如定宽整数）可直接退化为 {@link #between}。
     */
    T betweenJittered(T a, T b);

    /** 生成 {@code n} 个均匀分布在 {@code a} 与 {@code b} 之间的键（升序）。 */
    List<T> nBetween(T a, T b, int n);

    /**
     * 比较两个键的先后。{@code null} 视为最小（排在最前）。
     * @return 负整数、零、正整数分别表示 {@code a} 在前、相等、在后
     */
    int compare(T a, T b);

    /** 键是否为合法格式（用于校验外部来源的值）。 */
    boolean isValid(T key);
}
