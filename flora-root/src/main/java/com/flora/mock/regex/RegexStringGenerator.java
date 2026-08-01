package com.flora.mock.regex;

import com.flora.mock.regex.impl.RegexAllocator;
import com.flora.mock.regex.impl.RegexAtom;
import com.flora.mock.regex.impl.RegexParser;
import com.flora.tag.ThreadFragile;

import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;

/**
 * 正则表达式字符串生成器：构造匹配给定正则的随机字符串。
 * <p>支持语法：字面量、{@code .}（可打印 ASCII）、字符类 {@code [a-z]}/{@code [^...]}、
 * {@code \d} {@code \w} {@code \s} 及其取反 {@code \D} {@code \W} {@code \S}、
 * 转义 {@code \t} {@code \n} {@code \r} {@code \f} {@code \0}、
 * Unicode 属性 {@code \p{...}}（如 {@code \p{L}} {@code \p{Nd}}，支持 {@code \P{...}} 取反）、
 * 量词 {@code *} {@code +} {@code ?} {@code {n}} {@code {n,m}} {@code {n,}}（含懒惰后缀）、
 * 分组与交替 {@code (a|b)}、非捕获组 {@code (?:...)}；锚 {@code ^}/{@code $} 忽略。</p>
 * <p>不支持的结构（反向引用、环视、命名组、未知 Unicode 属性、非法量词、重复上限超阈值）
 * 抛出 {@link RegexGenerationException} 打断生成。通过 {@link #of(String, RandomGenerator)} 注入熵源，
 * 同一种子生成结果可复现。{@link #generate(int)} 可指定目标长度：可变长量词
 * （{@code *}/{@code +}/{@code {n,}}）朝目标长度靠拢，有界量词与固定量词不受影响。</p>
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

    /** @return 匹配 pattern 的随机字符串；遇到不支持的结构抛 {@link RegexGenerationException} */
    public String generate() {
        return generate(-1); // -1 表示无目标长度，按自身量词生成
    }

    /**
     * 生成匹配 pattern 的随机字符串，并尽力使长度接近 targetLength。
     * <p>采用两阶段弹性分配：先统计各原子的最小总长 totalMin，
     * target 不足则全部按最小值生成（硬约束优先）；target 超出则按各原子
     * 单次长度（estimate）为权重瓜分剩余配额，分配次数 clamp 在量词区间内。</p>
     *
     * @param targetLength 目标长度；&lt; 0 表示无目标，按自身量词生成
     * @return 匹配的随机字符串；不支持的结构抛 {@link RegexGenerationException}
     */
    public String generate(int targetLength) {
        try {
            RegexParser p = new RegexParser(pattern, random);
            List<RegexAtom> atoms = p.parseSequence();
            if (!p.isAtEnd()) {
                throw new RegexGenerationException("未消费完的正则: " + pattern);
            }
            if (targetLength >= 0) {
                RegexAllocator.allocate(atoms, targetLength);
            }
            StringBuilder sb = new StringBuilder();
            for (RegexAtom atom : atoms) {
                atom.append(sb);
            }
            return sb.toString();
        } catch (RegexGenerationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new RegexGenerationException("不支持的正则语法: " + pattern, e);
        }
    }

    /** 估算 pattern 生成的典型长度（供上层长度分配参考）；解析失败回退 8。 */
    public static int estimateLength(String pattern) {
        try {
            RegexParser p = new RegexParser(pattern, new Random());
            List<RegexAtom> atoms = p.parseSequence();
            if (!p.isAtEnd()) {
                return 8;
            }
            long total = 0;
            for (RegexAtom atom : atoms) {
                total += (atom.minTotal() + atom.maxTotal()) / 2;
            }
            return (int) Math.max(1, Math.min(total, 4096));
        } catch (RuntimeException e) {
            return 8;
        }
    }
}
