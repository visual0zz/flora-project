package com.flora.entropy.mesure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Entropy} 的度量契约测试：香农熵、归一化熵、字符类别、压缩复杂度比。
 */
class EntropyTest {

    @Test
    void shannonOfEmptyAndUniform() {
        assertEquals(0.0, Entropy.shannon(""), 1e-9);
        assertEquals(0.0, Entropy.shannon("aaaa"), 1e-9);
        // 16 个互异小写字母：均匀分布，香农熵 = log2(16) = 4 bit/字符
        assertEquals(4.0, Entropy.shannon("abcdefghijklmnop"), 1e-9);
    }

    @Test
    void shannonByteUniform() {
        byte[] uniform = new byte[256];
        for (int i = 0; i < 256; i++) {
            uniform[i] = (byte) i;
        }
        // 256 个互异字节：熵 = log2(256) = 8 bit/字节
        assertEquals(8.0, Entropy.shannon(uniform), 1e-9);
        assertEquals(0.0, Entropy.shannon(new byte[0]), 1e-9);
    }

    @Test
    void normalizedRangesAndUniform() {
        assertEquals(0.0, Entropy.normalized(""), 1e-9);
        assertEquals(0.0, Entropy.normalized("aaaa"), 1e-9);
        // 互异字母：归一化后应为 1.0（相对自身字母表完全随机）
        assertEquals(1.0, Entropy.normalized("abcdefghijklmnop"), 1e-9);
        // 随机且混合的串归一化熵接近上限，且不超过 1
        double r = Entropy.normalized("aB3kF9xQ2mNpLr7tVcWzQeXyZ");
        assertTrue(r > 0.8 && r <= 1.0, "随机串归一化熵应接近 1: " + r);
    }

    @Test
    void characterClassesCounts() {
        assertEquals(1, Entropy.characterClasses("abc"));
        assertEquals(1, Entropy.characterClasses("ABC"));
        assertEquals(1, Entropy.characterClasses("123"));
        assertEquals(3, Entropy.characterClasses("aA1"));
        assertEquals(4, Entropy.characterClasses("aA1!"));
        // 空白与控制字符不计入类别
        assertEquals(1, Entropy.characterClasses("a b"));
        assertEquals(0, Entropy.characterClasses("   "));
        assertEquals(0, Entropy.characterClasses(""));
    }

    @Test
    void alnumClassesIgnoresSeparators() {
        // 只统计字母大小写与数字，分隔符/符号不计
        assertEquals(2, Entropy.alnumClasses("2024-01-15T103000Z")); // 大写+数字，时间戳=2
        assertEquals(2, Entropy.alnumClasses("e3b0c44298fc1c149"));   // 小写+数字，hex=2
        assertEquals(3, Entropy.alnumClasses("aB3kF9xQ2mNpLr7tVcWzQe")); // 大小写+数字=3
        assertEquals(1, Entropy.alnumClasses("normal-text-here"));  // 仅小写=1
        assertEquals(0, Entropy.alnumClasses(""));
        assertEquals(0, Entropy.alnumClasses("-_.+="));
    }

    @Test
    void complexityRatioDistinguishesRandomFromRepetitive() {
        // 高随机串基本不可压缩，比值接近 1
        double random = Entropy.complexityRatio("aB3kF9xQ2mNpLr7tVcWzQeXyZ0123456789");
        assertTrue(random > 0.8, "随机串压缩比应接近 1: " + random);
        // 高度重复的串可压缩，比值明显偏低
        double repetitive = Entropy.complexityRatio("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertTrue(repetitive < 0.5, "重复串压缩比应偏低: " + repetitive);
    }
}
