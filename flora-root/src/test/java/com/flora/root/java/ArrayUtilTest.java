package com.flora.root.java;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ArrayUtil 数组工具类的单元测试。
 * 覆盖 isEmpty/isNotEmpty（含原始类型数组）、contains/indexOf、subarray、
 * toList 以及 concat 合并。
 */
class ArrayUtilTest {

    // ==================== 判空 ====================

    @Test
    void isEmptyObjectArray() {
        assertTrue(ArrayUtil.isEmpty((Object[]) null));
        assertTrue(ArrayUtil.isEmpty(new String[0]));
        assertFalse(ArrayUtil.isEmpty(new String[]{"a"}));
    }

    @Test
    void isEmptyPrimitiveArrayViaReflection() {
        assertTrue(ArrayUtil.isEmpty(new int[0]));
        assertTrue(ArrayUtil.isEmpty((Object) new int[0]));
        assertFalse(ArrayUtil.isEmpty(new int[]{1, 2}));
    }

    @Test
    void isNotEmptyPrimitiveArray() {
        assertTrue(ArrayUtil.isNotEmpty(new long[]{1L}));
        assertFalse(ArrayUtil.isNotEmpty(new long[0]));
    }

    @Test
    void isEmptyRejectsNonArray() {
        assertThrows(IllegalArgumentException.class, () -> ArrayUtil.isEmpty("notArray"));
    }

    // ==================== 包含 / 索引 ====================

    @Test
    void containsAndIndexOf() {
        String[] a = {"x", "y", "z"};
        assertTrue(ArrayUtil.contains(a, "y"));
        assertFalse(ArrayUtil.contains(a, "w"));
        assertEquals(1, ArrayUtil.indexOf(a, "y"));
        assertEquals(-1, ArrayUtil.indexOf(a, "w"));
    }

    @Test
    void indexOfNullSafe() {
        assertEquals(-1, ArrayUtil.indexOf(null, "x"));
        assertTrue(ArrayUtil.contains(new String[]{"a", null, "b"}, null));
        assertEquals(1, ArrayUtil.indexOf(new String[]{"a", null, "b"}, null));
    }

    // ==================== 子数组 ====================

    @Test
    void subarrayBasic() {
        Integer[] src = {1, 2, 3, 4, 5};
        assertArrayEquals(new Integer[]{2, 3, 4}, ArrayUtil.subarray(src, 1, 4));
    }

    @Test
    void subarrayNegativeIndices() {
        Integer[] src = {1, 2, 3, 4, 5};
        assertArrayEquals(new Integer[]{4, 5}, ArrayUtil.subarray(src, -2, 5));
        assertArrayEquals(new Integer[]{3, 4}, ArrayUtil.subarray(src, -3, -1));
    }

    @Test
    void subarrayClampsOutOfBounds() {
        Integer[] src = {1, 2, 3};
        assertArrayEquals(new Integer[]{2, 3}, ArrayUtil.subarray(src, 1, 99));
        assertArrayEquals(new Integer[]{1, 2, 3}, ArrayUtil.subarray(src, -99, 99));
    }

    @Test
    void subarrayNullReturnsNull() {
        assertNull(ArrayUtil.subarray(null, 0, 1));
    }

    // ==================== 转列表 ====================

    @Test
    void toListDefensiveCopy() {
        Integer[] src = {1, 2, 3};
        List<Integer> list = ArrayUtil.toList(src);
        assertEquals(List.of(1, 2, 3), list);
        list.add(4);
        assertEquals(3, src.length);
        assertEquals(4, list.size());
    }

    @Test
    void toListNullReturnsEmpty() {
        assertTrue(ArrayUtil.toList(null).isEmpty());
    }

    // ==================== 合并 ====================

    @Test
    void concatArrays() {
        String[] a = {"a", "b"};
        String[] b = {"c"};
        String[] c = {"d", "e"};
        assertArrayEquals(new String[]{"a", "b", "c", "d", "e"}, ArrayUtil.concat(a, b, c));
    }

    @Test
    void concatSkipsNull() {
        String[] a = {"a"};
        assertArrayEquals(new String[]{"a"}, ArrayUtil.concat(a, null));
    }

    @Test
    void concatAllNullReturnsNull() {
        assertNull(ArrayUtil.concat(null, null));
    }
}
