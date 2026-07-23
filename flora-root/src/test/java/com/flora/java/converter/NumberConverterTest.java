package com.flora.java.converter;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NumberConverter 的完整单测。
 * 覆盖：8 种数值类型的字符串解析、自身类型原值返回、null 输入、边界值、异常输入。
 */
class NumberConverterTest {

    private final NumberConverter converter = new NumberConverter();

    // ========== null ==========

    /**
     * 测试 null 输入返回 null。
     */
    @Test
    void nullInputReturnsNull() {
        assertNull(converter.convert(null, Integer.class));
    }

    // ========== 自身类型原值返回 ==========

    /**
     * 测试 Integer 类型原值返回。
     */
    @Test
    void integerValueReturnsSelf() {
        Integer v = 42;
        assertSame(v, converter.convert(v, Integer.class));
    }

    /**
     * 测试 Long 类型原值返回。
     */
    @Test
    void longValueReturnsSelf() {
        Long v = 42L;
        assertSame(v, converter.convert(v, Long.class));
    }

    /**
     * 测试 BigDecimal 类型原值返回。
     */
    @Test
    void bigDecimalValueReturnsSelf() {
        BigDecimal v = BigDecimal.valueOf(1.5);
        assertSame(v, converter.convert(v, BigDecimal.class));
    }

    // ========== 字符串解析 ==========

    /**
     * 测试字符串转 Integer。
     */
    @Test
    void stringToInteger() {
        assertEquals(Integer.valueOf(42), converter.convert("42", Integer.class));
    }

    /**
     * 测试字符串转 Long。
     */
    @Test
    void stringToLong() {
        assertEquals(Long.valueOf(42L), converter.convert("42", Long.class));
    }

    /**
     * 测试字符串转 Double。
     */
    @Test
    void stringToDouble() {
        assertEquals(Double.valueOf(3.14), converter.convert("3.14", Double.class));
    }

    /**
     * 测试字符串转 Float。
     */
    @Test
    void stringToFloat() {
        assertEquals(Float.valueOf(2.5f), converter.convert("2.5", Float.class));
    }

    /**
     * 测试字符串转 Short。
     */
    @Test
    void stringToShort() {
        assertEquals(Short.valueOf((short) 123), converter.convert("123", Short.class));
    }

    /**
     * 测试字符串转 Byte。
     */
    @Test
    void stringToByte() {
        assertEquals(Byte.valueOf((byte) 127), converter.convert("127", Byte.class));
    }

    /**
     * 测试字符串转 BigDecimal。
     */
    @Test
    void stringToBigDecimal() {
        assertEquals(new BigDecimal("123.456"), converter.convert("123.456", BigDecimal.class));
    }

    /**
     * 测试字符串转 BigInteger。
     */
    @Test
    void stringToBigInteger() {
        assertEquals(new BigInteger("9999999999999999"), converter.convert("9999999999999999", BigInteger.class));
    }

    /**
     * 测试负整数字符串解析。
     */
    @Test
    void negativeInteger() {
        assertEquals(Integer.valueOf(-42), converter.convert("-42", Integer.class));
    }

    /**
     * 测试带空格的字符串解析（trim）。
     */
    @Test
    void trimmedString() {
        assertEquals(Integer.valueOf(7), converter.convert("  7  ", Integer.class));
    }

    /**
     * 测试 Integer 边界值。
     */
    @Test
    void integerBoundary() {
        assertEquals(Integer.valueOf(Integer.MAX_VALUE), converter.convert(String.valueOf(Integer.MAX_VALUE), Integer.class));
        assertEquals(Integer.valueOf(Integer.MIN_VALUE), converter.convert(String.valueOf(Integer.MIN_VALUE), Integer.class));
    }

    /**
     * 测试 Double 边界值。
     */
    @Test
    void doubleBoundary() {
        assertEquals(Double.valueOf(Double.MAX_VALUE), converter.convert(String.valueOf(Double.MAX_VALUE), Double.class));
        assertEquals(Double.valueOf(-Double.MAX_VALUE), converter.convert(String.valueOf(-Double.MAX_VALUE), Double.class));
    }

    // ========== 异常情况 ==========

    /**
     * 测试非法整数字符串抛出异常。
     */
    @Test
    void invalidIntegerThrows() {
        assertThrows(NumberFormatException.class, () -> converter.convert("not-a-number", Integer.class));
    }

    /**
     * 测试溢出 Long 范围的字符串抛出异常。
     */
    @Test
    void overflowLongThrows() {
        assertThrows(NumberFormatException.class,
                () -> converter.convert("99999999999999999999999999999", Long.class));
    }
}
