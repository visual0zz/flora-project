/**
 * 正则表达式字符串生成器包。
 * <p>入口为 {@link com.flora.mock.regex.RegexStringGenerator}，
 * 构造匹配给定正则的随机字符串。支持常用正则子集与 Unicode 属性，
 * 不支持的结构（反向引用、环视、命名组等）抛出 {@code RegexGenerationException}。
 * 内部实现位于 {@code com.flora.mock.regex.impl} 子包（不导出）。</p>
 */
package com.flora.mock.regex;
