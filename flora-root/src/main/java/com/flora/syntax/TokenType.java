package com.flora.syntax;

/**
 * 宽泛词法类别。
 * <p>不含具体运算符/定界符——具体符号的值一律放在 {@link Token#value()} 字符串里
 * （如 expr 的 {@code "+"}、{@code "<<"}，bracket 的 {@code "<%"}、{@code "%>"}）。
 * 这样保持类别有限且通用，避免为每个分析器膨胀枚举。</p>
 */
public enum TokenType {
    /** 被动文本（bracket 的括号外内容；expr 不使用）。 */
    TEXT,
    /** 数字字面量（expr）。 */
    NUMBER,
    /** 标识符（expr）。 */
    IDENT,
    /** 任意符号/运算符（expr 的运算符都在 value 里）。 */
    SYMBOL,
    /** 开定界符（value 存实际的左定界符串，如 {@code "("}、{@code "<%"}）。 */
    OPEN,
    /** 闭定界符（value 存实际的右定界符串）。 */
    CLOSE,
    /** 输入结束。 */
    EOF
}
