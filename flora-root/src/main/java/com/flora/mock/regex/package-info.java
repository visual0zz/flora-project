/**
 * 正则表达式字符串生成器包。
 * <p>入口为 {@link com.flora.mock.regex.RegexStringGenerator}，
 * 基于 {@code com.flora.mock.automaton} 自动机构造匹配给定正则的随机字符串。
 * 支持扩展语法（字符类/简写/转义/Unicode 属性/量词/分组/交替/POSIX/并集差集），
 * 不支持的结构（反向引用、环视、命名组等）抛出 {@code RegexGenerationException}。
 * 内部 Unicode 区间池位于 {@code com.flora.mock.regex.impl} 子包（不导出）。</p>
 */
package com.flora.mock.regex;
