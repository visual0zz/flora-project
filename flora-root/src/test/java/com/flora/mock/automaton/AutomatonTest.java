package com.flora.mock.automaton;

import com.flora.mock.regex.automaton.Automaton;
import com.flora.mock.regex.automaton.AutomatonException;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link Automaton} 测试。
 * 核心策略：采样结果必须通过 {@link Pattern#matches} 校验（结构保证）。
 */
class AutomatonTest {

    private final Random random = new Random(42L);

    private String sample(String regex, int target) {
        return Automaton.compile(regex).sample(target, random);
    }

    private void assertSamplesMatch(String regex) {
        Automaton a = Automaton.compile(regex);
        for (int i = 0; i < 50; i++) {
            String s = a.sample(random);
            assertNotNull(s);
            assertTrue(Pattern.matches(regex, s),
                    "采样结果不匹配 " + regex + ": " + s);
        }
    }

    // ── 基础语法 ──

    @Test
    void basicLiteralsMatch() {
        assertSamplesMatch("hello");
        assertSamplesMatch("a.b");
    }

    @Test
    void charClassesMatch() {
        assertSamplesMatch("[a-z]+");
        assertSamplesMatch("[^a-z]+");
        assertSamplesMatch("[a-f0-9]{2,4}");
    }

    @Test
    void shorthandMatch() {
        assertSamplesMatch("\\d+");
        assertSamplesMatch("\\w+");
        assertSamplesMatch("\\D+");
        assertSamplesMatch("\\W+");
    }

    @Test
    void quantifiersMatch() {
        assertSamplesMatch("a*");
        assertSamplesMatch("a+");
        assertSamplesMatch("a?");
        assertSamplesMatch("a{2}");
        assertSamplesMatch("a{2,4}");
        assertSamplesMatch("a{2,}");
    }

    @Test
    void lazyQuantifiersMatch() {
        assertSamplesMatch("a*?b");
        assertSamplesMatch("(ab|cd)*?x");
    }

    @Test
    void groupsAndAlternationMatch() {
        assertSamplesMatch("(ab|cd)+");
        assertSamplesMatch("(?:[a-z]{2})-\\d+");
    }

    @Test
    void unicodePropertyMatch() {
        assertSamplesMatch("\\p{L}+");
        assertSamplesMatch("\\p{Ll}+");
        assertSamplesMatch("\\p{Nd}+");
        assertSamplesMatch("\\P{L}+");
    }

    // ── 扩展语法 ──

    @Test
    void posixClassMatch() {
        assertSamplesMatch("[[:alpha:]]+");
        assertSamplesMatch("[[:digit:]]{3}");
        assertSamplesMatch("[[:alnum:]]+");
    }

    @Test
    void intersectionMatch() {
        // [a-z&&[^aeiou]] = 辅音字母
        assertSamplesMatch("[a-z&&[^aeiou]]+");
    }

    @Test
    void hexEscapeMatch() {
        assertSamplesMatch("\\x{41}");  // 'A'
        assertSamplesMatch("\\u0042");  // 'B'
    }

    @Test
    void nestedCharClassMatch() {
        assertSamplesMatch("[a-z[0-9]]+");
    }

    // ── 按目标长度采样 ──

    @Test
    void sampleApproachesTarget() {
        Automaton a = Automaton.compile("[a-z]*");
        for (int i = 0; i < 30; i++) {
            String s = a.sample(20, random);
            assertTrue(s.length() >= 15 && s.length() <= 30,
                    "应朝目标 20 靠拢: " + s.length());
        }
    }

    @Test
    void sampleBelowMinUsesMin() {
        // target=2 小于 a{5,} 的最小长度 5，应生成至少 5 个
        Automaton a = Automaton.compile("a{5,}");
        String s = a.sample(2, random);
        assertTrue(s.length() >= 5, "应至少 5 个: " + s.length());
    }

    @Test
    void fixedQuantifierIgnoresTarget() {
        Automaton a = Automaton.compile("a{3}");
        assertEquals(3, a.sample(20, random).length());
    }

    // ── 匹配 ──

    @Test
    void matchesWorks() {
        Automaton a = Automaton.compile("a(b|c)d");
        assertTrue(a.matches("abd"));
        assertTrue(a.matches("acd"));
        assertFalse(a.matches("ad"));
        assertFalse(a.matches("abcd"));
    }

    // ── 可满足性 ──

    @Test
    void satisfiability() {
        assertTrue(Automaton.compile("[a-z]+").isSatisfiable());
        assertTrue(Automaton.compile("a|b").isSatisfiable());
    }

    // ── 代数运算 ──

    @Test
    void unionAcceptsEither() {
        Automaton u = Automaton.compile("ab").union(Automaton.compile("cd"));
        assertTrue(u.matches("ab"));
        assertTrue(u.matches("cd"));
        assertFalse(u.matches("ac"));
    }

    @Test
    void intersectAcceptsBoth() {
        Automaton i = Automaton.compile("[a-z]+").intersect(Automaton.compile("a+"));
        assertTrue(i.matches("aaa"));
        assertFalse(i.matches("bbb"));
        assertTrue(Pattern.matches("a+", i.sample(random)));
    }

    @Test
    void complementRejectsOriginal() {
        Automaton c = Automaton.compile("ab").complement();
        assertFalse(c.matches("ab"));
        assertTrue(c.matches("abc"));
        assertTrue(c.matches(""));
    }

    // ── 不兼容语法 → 抛异常 ──

    @Test
    void unsupportedStructuresThrow() {
        assertThrows(AutomatonException.class, () -> Automaton.compile("(a)\\1"));       // 反向引用
        assertThrows(AutomatonException.class, () -> Automaton.compile("(?=a)"));        // 前瞻
        assertThrows(AutomatonException.class, () -> Automaton.compile("(?!a)"));        // 负前瞻
        assertThrows(AutomatonException.class, () -> Automaton.compile("(?<=a)"));       // 后顾
        assertThrows(AutomatonException.class, () -> Automaton.compile("(?<name>a)"));   // 命名组
        assertThrows(AutomatonException.class, () -> Automaton.compile("\\p{Foo}"));     // 未知属性
        assertThrows(AutomatonException.class, () -> Automaton.compile("[a-z"));         // 字符类未闭合
        assertThrows(AutomatonException.class, () -> Automaton.compile("(abc"));         // 分组未闭合
        assertThrows(AutomatonException.class, () -> Automaton.compile("a{10000}"));     // 超阈值
    }

    // ── 空语言 ──

    @Test
    void emptyLanguageSampleThrows() {
        // [a&&b] 空交集 → 空语言
        Automaton a = Automaton.compile("[a&&b]");
        assertFalse(a.isSatisfiable());
        assertThrows(AutomatonException.class, () -> a.sample(random));
    }
}
