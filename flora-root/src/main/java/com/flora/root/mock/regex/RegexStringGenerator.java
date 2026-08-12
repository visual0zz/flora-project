package com.flora.root.mock.regex;

import com.flora.root.mock.regex.automaton.Automaton;
import com.flora.root.mock.regex.automaton.AutomatonException;
import com.flora.root.tag.ModuleEntry;
import com.flora.root.tag.ThreadFragile;

import java.util.Random;
import java.util.random.RandomGenerator;

/**
 * 正则表达式字符串生成器：构造匹配给定正则的随机字符串。
 * <p>基于自研正则自动机（NFA/DFA），生成结果必然整体匹配 pattern（结构保证），
 * 指定目标长度时从自动机按长度采样（无拒绝采样、无回溯）。</p>
 *
 * <p><b>支持的语法</b>：
 * 字面量、{@code .}（除行终止符外任意字符）、
 * 字符类 {@code [a-z]}/{@code [^...]}/范围/内嵌简写/嵌套字符类 {@code [a-z[0-9]]}/
 * 交集 {@code [a-z&&[^aeiou]]}、简写 {@code \d \w \s \D \W \S}、
 * 转义 {@code \t \n \r \f \0}、十六进制 {@code \x{1F}}/{@code \xNN}、
 * Unicode 转义 {@code \u0041}、Unicode 属性 {@code \p{L}}/{@code \P{L}}、
 * 量词 {@code * + ? {n} {n,m} {n,}}（懒惰后缀 {@code ?} 忽略）、
 * 分组与交替 {@code (a|b)}、非捕获组 {@code (?:...)}；
 * 锚 {@code ^}/{@code $} 忽略。</p>
 *
 * <p><b>不支持的语法</b>（编译期抛 {@link RegexGenerationException} 打断，不做静默处理）：
 * 反向引用 {@code \1}、环视 {@code (?=...)}/{@code (?!...)}/{@code (?<=...)}、
 * 命名组 {@code (?<name>...)}、未知 Unicode 属性、非法/超阈值量词
 * （单次重复上限 256）、未闭合的字符类/分组/量词。</p>
 *
 * <p>注意：本生成器与校验侧（{@code JsonSchema} 的 {@code pattern} 校验，使用
 * JDK {@code java.util.regex} 全特性）语法范围不同——生成器是校验器的子集；
 * 生成结果整体匹配，必然通过校验侧的 {@code find()} 搜索判定。</p>
 *
 * <pre>{@code
 * String value = RegexStringGenerator.of("[a-z]{2,4}").generate();
 * }</pre>
 */
@ModuleEntry
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
