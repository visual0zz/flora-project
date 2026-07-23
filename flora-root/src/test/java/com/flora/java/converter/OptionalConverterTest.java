package com.flora.java.converter;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OptionalConverter 的单测。
 * 验证任意值包装为 Optional 的行为。
 */
class OptionalConverterTest {

    private final OptionalConverter converter = new OptionalConverter();

    /**
     * 测试非 null 值包装为 Optional.of。
     */
    @Test
    void nonNullWrapsInOptional() {
        Optional<?> result = (Optional<?>) converter.convert("hello", Optional.class);
        assertEquals(Optional.of("hello"), result);
    }

    /**
     * 测试 null 变为 Optional.empty。
     */
    @Test
    void nullBecomesOptionalEmpty() {
        Optional<?> result = (Optional<?>) converter.convert(null, Optional.class);
        assertEquals(Optional.empty(), result);
    }

    /**
     * 测试整数包装为 Optional。
     */
    @Test
    void integerWrapped() {
        Optional<?> result = (Optional<?>) converter.convert(42, Optional.class);
        assertEquals(Optional.of(42), result);
    }

    /**
     * 测试默认优先级为 0。
     */
    @Test
    void priorityIsDefault() {
        assertEquals(0, converter.declarePriority());
    }
}
