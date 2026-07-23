package com.flora.java.converter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EnumConverter 的完整单测。
 * 覆盖：null 输入、枚举常量名精确匹配/忽略大小写匹配、ordinal 数值匹配、异常输入。
 */
class EnumConverterTest {

    private final EnumConverter converter = new EnumConverter();

    private enum Color {RED, GREEN, BLUE}
    private enum Status {ACTIVE, INACTIVE}

    /**
     * 测试 null 输入返回 null。
     */
    @Test
    void nullInputReturnsNull() {
        assertNull(converter.convert(null, Color.class));
    }

    // ========== 自身类型原值返回 ==========

    /**
     * 测试枚举值原值返回。
     */
    @Test
    void enumValueReturnsSelf() {
        assertSame(Color.RED, converter.convert(Color.RED, Color.class));
    }

    // ========== 精确名匹配 ==========

    /**
     * 测试精确枚举常量名匹配。
     */
    @Test
    void exactNameMatch() {
        assertEquals(Color.RED, converter.convert("RED", Color.class));
        assertEquals(Color.GREEN, converter.convert("GREEN", Color.class));
        assertEquals(Color.BLUE, converter.convert("BLUE", Color.class));
    }

    /**
     * 测试字符串 trim 后匹配。
     */
    @Test
    void stringTrimmedBeforeMatch() {
        assertEquals(Color.RED, converter.convert("  RED  ", Color.class));
    }

    // ========== 忽略大小写匹配 ==========

    /**
     * 测试大小写不敏感的枚举名匹配。
     */
    @Test
    void caseInsensitiveMatch() {
        assertEquals(Color.RED, converter.convert("red", Color.class));
        assertEquals(Color.GREEN, converter.convert("Green", Color.class));
        assertEquals(Color.BLUE, converter.convert("BLUE", Color.class));
    }

    // ========== ordinal 数值匹配 ==========

    /**
     * 测试 ordinal 数值匹配。
     */
    @Test
    void ordinalMatch() {
        assertEquals(Color.RED, converter.convert("0", Color.class));
        assertEquals(Color.GREEN, converter.convert("1", Color.class));
        assertEquals(Color.BLUE, converter.convert("2", Color.class));
    }

    /**
     * 测试 ordinal 超出范围时抛出异常。
     */
    @Test
    void ordinalOutOfBoundsThrows() {
        assertThrows(IllegalArgumentException.class, () -> converter.convert("3", Color.class));
        assertThrows(IllegalArgumentException.class, () -> converter.convert("-1", Color.class));
    }

    // ========== 异常情况 ==========

    /**
     * 测试无匹配字符串抛出异常。
     */
    @Test
    void noMatchThrows() {
        assertThrows(IllegalArgumentException.class, () -> converter.convert("YELLOW", Color.class));
    }

    /**
     * 测试非枚举目标类型抛出异常。
     */
    @Test
    void nonEnumTargetThrows() {
        assertThrows(IllegalArgumentException.class, () -> converter.convert("x", String.class));
    }

    /**
     * 测试不同枚举类型的同名常量正确匹配。
     */
    @Test
    void differentEnum() {
        Object result = converter.convert("ACTIVE", Status.class);
        assertEquals(Status.ACTIVE, result);
        assertEquals(Status.ACTIVE, converter.convert("ACTIVE", Status.class));
    }

    /**
     * 测试数字字符串优先按名称匹配，回退到 ordinal。
     */
    @Test
    void numberStringPreferredOverOrdinal() {
        assertEquals(Color.GREEN, converter.convert("1", Color.class));
    }

    /**
     * 测试数字字符串作为名称匹配失败后回退到 ordinal。
     */
    @Test
    void numberStringAsNameNotMatchThenFallbackToOrdinal() {
        assertEquals(Color.RED, converter.convert("0", Color.class));
    }
}
