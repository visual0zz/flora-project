package com.flora.java.converter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StringConverter 的完整单测。
 * 覆盖：null 输入、已是字符串、数值/布尔/数组/普通对象转字符串。
 */
class StringConverterTest {

    private final StringConverter converter = new StringConverter();

    /**
     * 测试 null 输入返回 null。
     */
    @Test
    void nullInputReturnsNull() {
        assertNull(converter.convert(null, String.class));
    }

    /**
     * 测试已为字符串时返回自身。
     */
    @Test
    void stringInputReturnsSelf() {
        String s = "hello";
        assertSame(s, converter.convert(s, String.class));
    }

    /**
     * 测试整数转字符串。
     */
    @Test
    void integerToString() {
        assertEquals("42", converter.convert(42, String.class));
    }

    /**
     * 测试布尔值转字符串。
     */
    @Test
    void booleanToString() {
        assertEquals("true", converter.convert(true, String.class));
    }

    /**
     * 测试数组转字符串。
     */
    @Test
    void arrayToString() {
        assertEquals("[1, 2, 3]", converter.convert(new int[]{1, 2, 3}, String.class));
    }

    /**
     * 测试普通对象通过 toString() 转字符串。
     */
    @Test
    void objectToString() {
        Object obj = new Object();
        assertEquals(obj.toString(), converter.convert(obj, String.class));
    }

    /**
     * 测试浮点数转字符串。
     */
    @Test
    void doubleToString() {
        assertEquals("3.14", converter.convert(3.14, String.class));
    }

    /**
     * 测试 null 字符串输入返回 null。
     */
    @Test
    void nullStringInputReturnsNull() {
        String s = null;
        assertNull(converter.convert(s, String.class));
    }
}
