package com.flora.java.converter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ArrayConverter 的完整单测。
 * 覆盖：数组间转换、Collection 转数组、单值转数组、elementType 参数控制组件类型及异常情况。
 */
class ArrayConverterTest {

    private final ArrayConverter converter = new ArrayConverter();

    /**
     * 测试 null 输入返回 null。
     */
    @Test
    void nullInputReturnsNull() {
        assertNull(converter.convert(null, String[].class));
    }

    // ========== 数组 -> 数组 ==========

    /**
     * 测试相同组件类型的数组返回自身。
     */
    @Test
    void sameComponentTypeArrayReturnsSelf() {
        String[] src = {"a", "b"};
        assertSame(src, converter.convert(src, String[].class));
    }

    /**
     * 测试 String[] 到 Integer[] 的元素类型转换。
     */
    @Test
    void stringArrayToIntegerArray() {
        Integer[] result = (Integer[]) converter.convert(new String[]{"1", "2", "3"}, Integer[].class);
        assertArrayEquals(new Integer[]{1, 2, 3}, result);
    }

    /**
     * 测试 int[] 到 String[] 的元素类型转换。
     */
    @Test
    void intArrayToStringArray() {
        String[] result = (String[]) converter.convert(new int[]{1, 2}, String[].class);
        assertArrayEquals(new String[]{"1", "2"}, result);
    }

    /**
     * 测试空数组的转换。
     */
    @Test
    void emptyArray() {
        String[] result = (String[]) converter.convert(new String[0], String[].class);
        assertArrayEquals(new String[0], result);
    }

    // ========== Collection -> 数组 ==========

    /**
     * 测试 Collection 转 String[]。
     */
    @Test
    void collectionToStringArray() {
        String[] result = (String[]) converter.convert(List.of("a", "b", "c"), String[].class);
        assertArrayEquals(new String[]{"a", "b", "c"}, result);
    }

    /**
     * 测试 Collection 转 Integer[]（含元素类型转换）。
     */
    @Test
    void collectionToIntegerArray() {
        Integer[] result = (Integer[]) converter.convert(List.of("1", "2"), Integer[].class);
        assertArrayEquals(new Integer[]{1, 2}, result);
    }

    // ========== 单值 -> 数组 ==========

    /**
     * 测试单值转数组。
     */
    @Test
    void singleValueToArray() {
        String[] result = (String[]) converter.convert("x", String[].class);
        assertArrayEquals(new String[]{"x"}, result);
    }

    /**
     * 测试整数单值转数组。
     */
    @Test
    void integerToArray() {
        Integer[] result = (Integer[]) converter.convert(42, Integer[].class);
        assertArrayEquals(new Integer[]{42}, result);
    }

    // ========== 通过 elementType 参数指定组件类型 ==========

    /**
     * 测试通过 elementType 参数控制组件类型。
     */
    @Test
    void elementTypeControlsComponentType() {
        String[] result = (String[]) converter.convert(
                List.of(1, 2, 3), String[].class, String.class);
        assertArrayEquals(new String[]{"1", "2", "3"}, result);
    }

    // ========== 异常情况 ==========

    /**
     * 测试非数组目标类型时抛出异常。
     */
    @Test
    void nonArrayTargetThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> converter.convert("x", String.class));
    }
}
