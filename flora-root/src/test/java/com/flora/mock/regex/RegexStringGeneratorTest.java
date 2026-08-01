package com.flora.mock.regex;

import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link RegexStringGenerator} 测试。
 * 核心策略：生成结果用 {@link Pattern#matches} 校验是否匹配原正则。
 */
class RegexStringGeneratorTest {

    private final Random random = new Random(42L);

    private String gen(String regex) {
        return RegexStringGenerator.of(regex, random).generate();
    }

    private void assertMatches(String regex) {
        for (int i = 0; i < 50; i++) {
            String value = gen(regex);
            assertNotNull(value, "应能生成: " + regex);
            assertTrue(Pattern.matches(regex, value),
                    "生成结果不匹配 " + regex + ": " + value);
        }
    }

    // ── 基础 ──

    @Test
    void generatesLiteral() {
        assertMatches("hello");
        assertMatches("a/b.c");
    }

    @Test
    void generatesDot() {
        assertMatches("a.b");
        assertMatches("....");
    }

    @Test
    void ignoresAnchors() {
        assertMatches("^abc$");
    }

    // ── 字符类 ──

    @Test
    void generatesCharClass() {
        assertMatches("[a-z]+");
        assertMatches("[0-9]{3}");
        assertMatches("[a-f0-9]+");
    }

    @Test
    void generatesNegatedCharClass() {
        assertMatches("[^a-z]+");
        assertMatches("[^0-9]+");
    }

    @Test
    void charClassWithRanges() {
        assertMatches("[A-Cx-z]+");
    }

    // ── 转义类 ──

    @Test
    void generatesShorthandClasses() {
        assertMatches("\\d+");
        assertMatches("\\w+");
        assertMatches("\\s");
    }

    @Test
    void generatesNegatedShorthand() {
        assertMatches("\\D+");
        assertMatches("\\W+");
        assertMatches("\\S+");
    }

    @Test
    void generatesEscapedWhitespace() {
        assertMatches("a\\tb");
        assertMatches("a\\nb");
    }

    @Test
    void charClassWithShorthand() {
        assertMatches("[\\d]+");
        assertMatches("[a-z\\d]+");
    }

    // ── 量词 ──

    @Test
    void generatesQuantifiers() {
        assertMatches("a*");
        assertMatches("a+");
        assertMatches("a?");
        assertMatches("a{2}");
        assertMatches("a{2,4}");
        assertMatches("a{2,}");
    }

    @Test
    void generatesLazyQuantifiers() {
        assertMatches("a*?");
        assertMatches("a+?");
        assertMatches("a{2,4}?");
    }

    @Test
    void lazyQuantifierFollowedByLiteralMatches() {
        // a*? 后跟字面量 b：生成结果必须整体匹配 ^a*?b$
        for (int i = 0; i < 100; i++) {
            String value = gen("a*?b");
            assertNotNull(value, "应能生成 a*?b");
            assertTrue(Pattern.matches("a*?b", value),
                    "a*?b 生成结果不匹配: " + value);
            assertTrue(value.endsWith("b"), "应以 b 结尾: " + value);
        }
    }

    @Test
    void lazyGroupFollowedByLiteralMatches() {
        // (ab|cd)*? 后跟字面量 x
        for (int i = 0; i < 100; i++) {
            String value = gen("(ab|cd)*?x");
            assertNotNull(value, "应能生成 (ab|cd)*?x");
            assertTrue(Pattern.matches("(ab|cd)*?x", value),
                    "(ab|cd)*?x 生成结果不匹配: " + value);
        }
    }

    @Test
    void quantifierUpperBound() {
        assertEquals(3, gen("a{3}").length());
        // 超阈值 → 抛异常打断
        assertThrows(RegexGenerationException.class, () -> gen("a{10000}"));
    }

    // ── 分组与交替 ──

    @Test
    void generatesAlternation() {
        assertMatches("(ab|cd)+");
        assertMatches("(red|green|blue)");
    }

    @Test
    void generatesNonCapturingGroup() {
        assertMatches("(?:ab|cd)+");
        assertMatches("(?:[a-z]{2})-\\d+");
    }

    // ── Unicode 属性 ──

    @Test
    void generatesUnicodeProperty() {
        assertMatches("\\p{L}+");
        assertMatches("\\p{Ll}+");
        assertMatches("\\p{Lu}+");
        assertMatches("\\p{Nd}+");
        assertMatches("\\p{P}+");
    }

    @Test
    void generatesNegatedUnicodeProperty() {
        assertMatches("\\P{L}+");
    }

    @Test
    void unknownUnicodePropertyThrows() {
        assertThrows(RegexGenerationException.class, () -> gen("\\p{Foo}"));
        assertThrows(RegexGenerationException.class, () -> gen("\\p{"));
    }

    // ── 不支持结构 → 抛异常打断 ──

    @Test
    void unsupportedStructuresThrow() {
        assertThrows(RegexGenerationException.class, () -> gen("(a)\\1"));      // 反向引用
        assertThrows(RegexGenerationException.class, () -> gen("(?=a)"));       // 前瞻
        assertThrows(RegexGenerationException.class, () -> gen("(?!a)"));       // 负前瞻
        assertThrows(RegexGenerationException.class, () -> gen("(?<=a)"));      // 后顾
        assertThrows(RegexGenerationException.class, () -> gen("(?<name>a)"));  // 命名组
        assertThrows(RegexGenerationException.class, () -> gen("\\p{NotAClass}")); // 未知属性
    }

    @Test
    void emptyOrMalformedPattern() {
        assertEquals("", gen(""));  // 空正则匹配空串
        assertThrows(RegexGenerationException.class, () -> gen("[a-z"));    // 字符类未闭合
        assertThrows(RegexGenerationException.class, () -> gen("(abc"));    // 分组未闭合
        assertThrows(RegexGenerationException.class, () -> gen("a{2,3"));  // 量词未闭合
    }

    // ── 种子可复现 ──

    @Test
    void seedIsReproducible() {
        String regex = "(ab|cd)\\d{2,4}[a-z]?";
        String v1 = RegexStringGenerator.of(regex, new Random(7L)).generate();
        String v2 = RegexStringGenerator.of(regex, new Random(7L)).generate();
        assertEquals(v1, v2);
        assertTrue(Pattern.matches(regex, v1));
    }

    // ── 上限保护 ──

    @Test
    void boundedGenerationLength() {
        // 无限量词被限制在合理长度
        String value = gen("a{200,}");
        assertNotNull(value);
        assertTrue(value.length() >= 200 && value.length() <= 256);
    }

    // ── 推荐长度 ──

    @Test
    void unboundedQuantifierApproachesTarget() {
        for (int i = 0; i < 50; i++) {
            String value = genWithTarget("[a-z]*", 20);
            assertTrue(value.length() >= 15 && value.length() <= 30,
                    "应朝目标 20 靠拢: " + value.length());
            assertTrue(Pattern.matches("[a-z]*", value));
        }
    }

    @Test
    void fixedQuantifierIgnoresTarget() {
        // 固定量词是硬约束，target 不影响
        for (int i = 0; i < 20; i++) {
            assertEquals(3, genWithTarget("a{3}", 20).length());
        }
    }

    @Test
    void minBoundBeatsSmallTarget() {
        // target 小于量词下界时，按量词下界生成
        String value = genWithTarget("a{5,}", 2);
        assertNotNull(value);
        assertTrue(value.length() >= 5, "target=2 但量词至少 5: " + value.length());
    }

    @Test
    void groupWithTarget() {
        for (int i = 0; i < 50; i++) {
            String value = genWithTarget("(ab|cd)+", 20);
            assertTrue(value.length() >= 10 && value.length() <= 30,
                    "分组应朝目标 20 靠拢: " + value.length());
            assertTrue(Pattern.matches("(ab|cd)+", value));
        }
    }

    @Test
    void estimateLengthReturnsReasonable() {
        assertTrue(RegexStringGenerator.estimateLength("a{3}") >= 3);
        assertTrue(RegexStringGenerator.estimateLength("[a-z]*") >= 1);
        assertTrue(RegexStringGenerator.estimateLength("(ab|cd){2,4}") >= 4);
    }

    private String genWithTarget(String regex, int target) {
        return RegexStringGenerator.of(regex, random).generate(target);
    }
}
