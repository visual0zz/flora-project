package com.flora.java.converter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NoopConverter 的单测。
 * 验证原值返回行为。
 */
class NoopConverterTest {

    private final NoopConverter converter = new NoopConverter();

    /**
     * 测试原值返回。
     */
    @Test
    void returnsSameObject() {
        Object obj = new Object();
        assertSame(obj, converter.convert(obj, Object.class));
    }

    /**
     * 测试 null 输入返回 null。
     */
    @Test
    void returnsNullForNullInput() {
        assertNull(converter.convert(null, Object.class));
    }

    /**
     * 测试字符串的 identity 转换。
     */
    @Test
    void stringIdentity() {
        String s = "hello";
        assertSame(s, converter.convert(s, String.class));
    }

    /**
     * 测试数值的向上转型 identity。
     */
    @Test
    void numericIdentity() {
        Integer v = 42;
        assertSame(v, converter.convert(v, Number.class));
    }

    /**
     * 测试优先级为 Integer.MIN_VALUE。
     */
    @Test
    void priorityIsMinValue() {
        assertEquals(Integer.MIN_VALUE, converter.declarePriority());
    }
}
