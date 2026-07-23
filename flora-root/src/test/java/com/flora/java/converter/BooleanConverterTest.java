package com.flora.java.converter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BooleanConverter 的完整单测。
 * 覆盖：Boolean 原值返回、各种 true/false 字符串、数值条件、null 输入、大小写不敏感。
 */
class BooleanConverterTest {

    private final BooleanConverter converter = new BooleanConverter();

    /**
     * 测试 null 输入返回 null。
     */
    @Test
    void nullInputReturnsNull() {
        assertNull(converter.convert(null, Boolean.class));
    }

    /**
     * 测试 Boolean.TRUE 原值返回。
     */
    @Test
    void booleanTrueReturnsSelf() {
        assertSame(Boolean.TRUE, converter.convert(true, Boolean.class));
    }

    /**
     * 测试 Boolean.FALSE 原值返回。
     */
    @Test
    void booleanFalseReturnsSelf() {
        assertSame(Boolean.FALSE, converter.convert(false, Boolean.class));
    }

    // ========== 数值输入 ==========

    /**
     * 测试非零数值转换为 true。
     */
    @Test
    void nonZeroNumberIsTrue() {
        assertEquals(Boolean.TRUE, converter.convert(1, Boolean.class));
        assertEquals(Boolean.TRUE, converter.convert(-1, Boolean.class));
        assertEquals(Boolean.TRUE, converter.convert(3.14, Boolean.class));
    }

    /**
     * 测试零值转换为 false。
     */
    @Test
    void zeroNumberIsFalse() {
        assertEquals(Boolean.FALSE, converter.convert(0, Boolean.class));
        assertEquals(Boolean.FALSE, converter.convert(0.0, Boolean.class));
        assertEquals(Boolean.FALSE, converter.convert(0L, Boolean.class));
    }

    // ========== 字符串 true 值 ==========

    /**
     * 测试 "true" 转换为 true。
     */
    @Test
    void stringTrue() {
        assertEquals(Boolean.TRUE, converter.convert("true", Boolean.class));
    }

    /**
     * 测试 "yes" 转换为 true。
     */
    @Test
    void stringYes() {
        assertEquals(Boolean.TRUE, converter.convert("yes", Boolean.class));
    }

    /**
     * 测试 "on" 转换为 true。
     */
    @Test
    void stringOn() {
        assertEquals(Boolean.TRUE, converter.convert("on", Boolean.class));
    }

    /**
     * 测试 "1" 转换为 true。
     */
    @Test
    void stringOne() {
        assertEquals(Boolean.TRUE, converter.convert("1", Boolean.class));
    }

    // ========== 字符串 false 值 ==========

    /**
     * 测试 "false" 转换为 false。
     */
    @Test
    void stringFalse() {
        assertEquals(Boolean.FALSE, converter.convert("false", Boolean.class));
    }

    /**
     * 测试 "off" 转换为 false。
     */
    @Test
    void stringOff() {
        assertEquals(Boolean.FALSE, converter.convert("off", Boolean.class));
    }

    /**
     * 测试 "no" 转换为 false。
     */
    @Test
    void stringNo() {
        assertEquals(Boolean.FALSE, converter.convert("no", Boolean.class));
    }

    /**
     * 测试 "0" 转换为 false。
     */
    @Test
    void stringZero() {
        assertEquals(Boolean.FALSE, converter.convert("0", Boolean.class));
    }

    /**
     * 测试未知字符串转换为 false。
     */
    @Test
    void stringUnknownIsFalse() {
        assertEquals(Boolean.FALSE, converter.convert("whatever", Boolean.class));
    }

    // ========== 大小写不敏感 ==========

    /**
     * 测试 true 值字符串的大小写不敏感性。
     */
    @Test
    void caseInsensitiveTrue() {
        assertEquals(Boolean.TRUE, converter.convert("True", Boolean.class));
        assertEquals(Boolean.TRUE, converter.convert("TRUE", Boolean.class));
        assertEquals(Boolean.TRUE, converter.convert("Yes", Boolean.class));
        assertEquals(Boolean.TRUE, converter.convert("ON", Boolean.class));
    }

    /**
     * 测试 false 值字符串的大小写不敏感性。
     */
    @Test
    void caseInsensitiveFalse() {
        assertEquals(Boolean.FALSE, converter.convert("False", Boolean.class));
        assertEquals(Boolean.FALSE, converter.convert("FALSE", Boolean.class));
    }

    /**
     * 测试带空格的输入。
     */
    @Test
    void trimmedInput() {
        assertEquals(Boolean.TRUE, converter.convert("  true  ", Boolean.class));
    }
}
