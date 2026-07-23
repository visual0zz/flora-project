package com.flora.codegen.engine.parser;

import com.flora.codegen.engine.CodeGenException;

import java.util.Objects;

/**
 * Lson 数字类型——统一代理模板表达式中所有数值的运算与比较。
 *
 * <p>Lson 解析数字字面量时统一返回 {@code LsonNumber}，替代原先 {@link Long}/{@link Double}
 * 混合类型。六类使用场景全部通过本类代理：
 *
 * <ol>
 *   <li><b>数值相等</b>（{@link #equals}）——以 {@code doubleValue} 为准，忽略内部表示差异；
 *       即 {@code LsonNumber.of(42).equals(LsonNumber.of(42.0))} 为 {@code true}。</li>
 *   <li><b>比较运算</b>（{@link #toDouble(Object, String)}）——统一转为 {@code double}。</li>
 *   <li><b>整数运算</b>（{@link #toLong(Object, String)}）——统一转为 {@code long}。</li>
 *   <li><b>字符串输出</b>（{@link #toString()}）——沿袭 {@code Long.toString}/{@code Double.toString}
 *       的 Java 默认格式。</li>
 *   <li><b>重复计数</b>（{@link #intValue}）——由 {@link Number#intValue()} 继承提供。</li>
 *   <li><b>类型匹配</b>——继承 {@link Number}，所有 {@code instanceof Number} 判断透明兼容。</li>
 * </ol>
 */
public final class LsonNumber extends Number implements Comparable<LsonNumber> {

    private final long longValue;
    private final double doubleValue;
    private final boolean isDouble;

    private LsonNumber(long value) {
        this.longValue = value;
        this.doubleValue = (double) value;
        this.isDouble = false;
    }

    private LsonNumber(double value) {
        this.longValue = (long) value;
        this.doubleValue = value;
        this.isDouble = true;
    }

    // ---- 工厂方法 ----

    /** 从 {@code long} 构造整数 LsonNumber。 */
    public static LsonNumber of(long value) {
        return new LsonNumber(value);
    }

    /** 从 {@code double} 构造浮点 LsonNumber。 */
    public static LsonNumber of(double value) {
        return new LsonNumber(value);
    }

    // ---- Number 抽象方法实现 ----

    @Override
    public int intValue() {
        return (int) longValue;
    }

    @Override
    public long longValue() {
        return longValue;
    }

    @Override
    public float floatValue() {
        return (float) doubleValue;
    }

    @Override
    public double doubleValue() {
        return doubleValue;
    }

    /** 是否为浮点（非整数）表示。 */
    public boolean isDouble() {
        return isDouble;
    }

    @Override
    public String toString() {
        return isDouble ? Double.toString(doubleValue) : Long.toString(longValue);
    }

    /**
     * 数值相等判断：仅比较 {@code doubleValue}，忽略内部 {@code long} / {@code double} 表示差异。
     * 即 {@code of(42).equals(of(42.0))} 返回 {@code true}。
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof LsonNumber other)) return false;
        return Double.doubleToLongBits(this.doubleValue) == Double.doubleToLongBits(other.doubleValue);
    }

    @Override
    public int hashCode() {
        return Double.hashCode(doubleValue);
    }

    @Override
    public int compareTo(LsonNumber o) {
        return Double.compare(this.doubleValue, o.doubleValue);
    }

    // ---- 安全转换工具（替换 BuiltinFunc/TemplateUtils 中分散的实现） ----

    /**
     * 从任意对象安全提取 {@code double} 值。
     *
     * @param v      待转换对象（{@link Number} 或 {@link Boolean}）
     * @param opName 调用函数名（用于异常消息）
     * @return 双精度浮点值
     * @throws CodeGenException 类型不支持转换时抛出
     */
    public static double toDouble(Object v, String opName) {
        if (v instanceof LsonNumber n) return n.doubleValue;
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof Boolean b) return b ? 1.0 : 0.0;
        throw new CodeGenException(
                opName + " 需要数值参数，实际为: " + (v == null ? "null" : v.getClass().getSimpleName()));
    }

    /**
     * 从任意对象安全提取 {@code long} 值（拒绝 {@link Boolean}）。
     *
     * @param v      待转换对象（{@link Number}）
     * @param opName 调用函数名（用于异常消息）
     * @return 长整型值
     * @throws CodeGenException 类型不支持转换时抛出
     */
    public static long toLong(Object v, String opName) {
        if (v instanceof LsonNumber n) return n.longValue;
        if (v instanceof Number n) return n.longValue();
        throw new CodeGenException(
                opName + " 需要整数参数，实际为: " + (v == null ? "null" : v.getClass().getSimpleName()));
    }
}
