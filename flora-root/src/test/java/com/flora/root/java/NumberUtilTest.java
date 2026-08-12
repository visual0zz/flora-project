package com.flora.root.java;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NumberUtil 数值工具类的单元测试。
 * 覆盖 clamp、isNumber、toInt/toLong/toDouble/toFloat 带默认值解析、
 * between/isInRange 范围判断，以及 max/min 极值计算。
 */
class NumberUtilTest {

    // ==================== clamp ====================

    @Test
    void clampInt() {
        assertEquals(5, NumberUtil.clamp(5, 0, 10));
        assertEquals(0, NumberUtil.clamp(-3, 0, 10));
        assertEquals(10, NumberUtil.clamp(99, 0, 10));
    }

    @Test
    void clampLong() {
        assertEquals(0L, NumberUtil.clamp(-3L, 0L, 10L));
        assertEquals(10L, NumberUtil.clamp(99L, 0L, 10L));
    }

    @Test
    void clampDouble() {
        assertEquals(1.5, NumberUtil.clamp(1.5, 0.0, 2.0));
        assertEquals(2.0, NumberUtil.clamp(3.0, 0.0, 2.0));
    }

    @Test
    void clampRejectsInvertedBounds() {
        assertThrows(IllegalArgumentException.class, () -> NumberUtil.clamp(5, 10, 0));
    }

    // ==================== isNumber ====================

    @Test
    void isNumberAcceptsValid() {
        assertTrue(NumberUtil.isNumber("123"));
        assertTrue(NumberUtil.isNumber("-12.5"));
        assertTrue(NumberUtil.isNumber("+0.5"));
        assertTrue(NumberUtil.isNumber(".5"));
        assertTrue(NumberUtil.isNumber("1.5e3"));
        assertTrue(NumberUtil.isNumber("-2E-4"));
    }

    @Test
    void isNumberRejectsInvalid() {
        assertFalse(NumberUtil.isNumber(null));
        assertFalse(NumberUtil.isNumber(""));
        assertFalse(NumberUtil.isNumber("abc"));
        assertFalse(NumberUtil.isNumber("1.2.3"));
        assertFalse(NumberUtil.isNumber("1e"));
        assertFalse(NumberUtil.isNumber("0x1F"));
    }

    // ==================== 安全解析 ====================

    @Test
    void toIntWithDefault() {
        assertEquals(42, NumberUtil.toInt(42, -1));
        assertEquals(42, NumberUtil.toInt("42", -1));
        assertEquals(42, NumberUtil.toInt("  42  ", -1));
        assertEquals(-1, NumberUtil.toInt(null, -1));
        assertEquals(-1, NumberUtil.toInt("abc", -1));
        assertEquals(-1, NumberUtil.toInt("", -1));
    }

    @Test
    void toLongWithDefault() {
        assertEquals(42L, NumberUtil.toLong(42L, -1L));
        assertEquals(42L, NumberUtil.toLong("42", -1L));
        assertEquals(-1L, NumberUtil.toLong("not-a-number", -1L));
    }

    @Test
    void toDoubleWithDefault() {
        assertEquals(1.5, NumberUtil.toDouble(1.5, 0.0));
        assertEquals(1.5, NumberUtil.toDouble("1.5", 0.0));
        assertEquals(0.0, NumberUtil.toDouble("abc", 0.0));
    }

    @Test
    void toFloatWithDefault() {
        assertEquals(1.5f, NumberUtil.toFloat(1.5f, 0.0f));
        assertEquals(1.5f, NumberUtil.toFloat("1.5", 0.0f));
        assertEquals(0.0f, NumberUtil.toFloat(null, 0.0f));
    }

    // ==================== 范围判断 ====================

    @Test
    void betweenInclusive() {
        assertTrue(NumberUtil.between(5, 0, 10));
        assertTrue(NumberUtil.between(0, 0, 10));
        assertTrue(NumberUtil.between(10, 0, 10));
        assertFalse(NumberUtil.between(-1, 0, 10));
        assertFalse(NumberUtil.between(11, 0, 10));
        assertFalse(NumberUtil.between(null, 0, 10));
    }

    @Test
    void betweenRejectsInvertedBounds() {
        assertThrows(IllegalArgumentException.class, () -> NumberUtil.between(5, 10, 0));
    }

    @Test
    void isInRangeGenericComparable() {
        assertTrue(NumberUtil.isInRange(5, 0, 10));
        assertTrue(NumberUtil.isInRange("b", "a", "c"));
        assertFalse(NumberUtil.isInRange("z", "a", "c"));
        assertFalse(NumberUtil.isInRange(null, "a", "c"));
    }

    @Test
    void isInRangeRejectsInvertedBounds() {
        assertThrows(IllegalArgumentException.class, () -> NumberUtil.isInRange(5, 10, 0));
    }

    // ==================== 极值 ====================

    @Test
    void maxIgnoresNull() {
        assertEquals(9, NumberUtil.max(1, 9, 4, null).intValue());
        assertEquals(9L, NumberUtil.max(1L, 9L).longValue());
    }

    @Test
    void minIgnoresNull() {
        assertEquals(1, NumberUtil.min(1, 9, 4, null).intValue());
    }

    @Test
    void maxReturnsNullForEmpty() {
        assertNull(NumberUtil.max());
        assertNull(NumberUtil.max((Number[]) null));
        assertNull(NumberUtil.max(null, null));
    }

    @Test
    void minReturnsNullForEmpty() {
        assertNull(NumberUtil.min());
        assertNull(NumberUtil.min(null, null));
    }

    @Test
    void maxMinWithBigDecimal() {
        BigDecimal a = new BigDecimal("1.2");
        BigDecimal b = new BigDecimal("3.4");
        assertEquals(b, NumberUtil.max(a, b));
        assertEquals(a, NumberUtil.min(a, b));
    }
}
