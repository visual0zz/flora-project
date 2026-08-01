package com.flora.mock.regex;

import com.flora.mock.automaton.Automaton;
import com.flora.mock.automaton.AutomatonException;
import com.flora.tag.ThreadFragile;

import java.util.Random;
import java.util.random.RandomGenerator;

/**
 * 正则表达式字符串生成器：构造匹配给定正则的随机字符串。
 * <p>基于自研正则自动机（NFA/DFA），支持扩展语法：字面量、{@code .}、字符类
 * （范围/取反/内嵌简写/POSIX/并集差集）、简写 {@code \d \w \s \D \W \S}、
 * 转义 {@code \t \n \r \f \0}、十六进制/Unicode 转义、Unicode 属性
 * {@code \p{...}}/{@code \P{...}}、量词 {@code * + ? {n} {n,m} {n,}}（含懒惰后缀）、
 * 分组与交替 {@code (a|b)}、非捕获组 {@code (?:...)}；锚 {@code ^}/{@code $} 忽略。</p>
 * <p>不支持的结构（反向引用、环视、命名组、未知属性、非法量词、未闭合结构）
 * 在编译期抛 {@link RegexGenerationException} 打断，不做静默处理。
 * {@link #generate(int)} 指定目标长度时，从自动机按长度采样（无拒绝采样）。</p>
 *
 * <pre>{@code
 * String value = RegexStringGenerator.of("[a-z]{2,4}").generate();
 * }</pre>
 */
@ThreadFragile("持有注入的共享 RandomGenerator，其线程安全性取决于实现，多线程并发 generate() 需自行同步")
public final class RegexStringGenerator {

    private final RandomGenerator random;
    private final String pattern;

    private RegexStringGenerator(String pattern, RandomGenerator random) {
        this.pattern = pattern;
        this.random = random;
    }

    /** 用默认随机源构造生成器。 */
    public static RegexStringGenerator of(String pattern) {
        return new RegexStringGenerator(pattern, new Random());
    }

    /** 注入熵源构造生成器（同一种子可复现）。 */
    public static RegexStringGenerator of(String pattern, RandomGenerator entropy) {
        return new RegexStringGenerator(pattern, entropy);
    }

    /** @return 匹配 pattern 的随机字符串；不支持的结构抛 {@link RegexGenerationException} */
    public String generate() {
        return generate(-1); // -1 表示无目标长度，自然采样
    }

    /**
     * 生成匹配 pattern 的随机字符串，并尽力使长度接近 targetLength。
     *
     * @param targetLength 目标长度；&lt; 0 表示无目标，自然采样
     * @return 匹配的随机字符串；不支持的结构抛 {@link RegexGenerationException}
     */
    public String generate(int targetLength) {
        try {
            return Automaton.compile(pattern).sample(targetLength, random);
        } catch (AutomatonException e) {
            throw new RegexGenerationException("不支持的正则语法: " + pattern, e);
        }
    }

    /** 估算 pattern 生成的典型长度（供上层长度分配参考）；编译失败回退 8。 */
    public static int estimateLength(String pattern) {
        try {
            return Automaton.compile(pattern).estimateLength();
        } catch (AutomatonException e) {
            return 8;
        }
    }
}
