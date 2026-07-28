package com.flora.java;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StrUtil 字符串工具类的单元测试。
 * 覆盖 equalsIgnoreCase、contains、split、substringBefore/After、isNumeric、strip，
 * 以及 toBytes/fromBytes 的 null 口径统一（null -> null）。
 */
class StrUtilTest {

    // ==================== 比较 ====================

    @Test
    void equalsIgnoreCaseNullSafe() {
        assertTrue(StrUtil.equalsIgnoreCase("abc", "ABC"));
        assertTrue(StrUtil.equalsIgnoreCase(null, null));
        assertFalse(StrUtil.equalsIgnoreCase("a", null));
        assertFalse(StrUtil.equalsIgnoreCase(null, "a"));
    }

    // ==================== 包含 ====================

    @Test
    void containsString() {
        assertTrue(StrUtil.contains("hello world", "world"));
        assertFalse(StrUtil.contains("hello", "x"));
        assertFalse(StrUtil.contains(null, "x"));
        assertFalse(StrUtil.contains("x", null));
    }

    @Test
    void containsChar() {
        assertTrue(StrUtil.contains("abc", 'b'));
        assertFalse(StrUtil.contains("abc", 'z'));
        assertFalse(StrUtil.contains(null, 'a'));
    }

    // ==================== 拆分 ====================

    @Test
    void splitBasic() {
        assertArrayEquals(new String[]{"a", "b", "c"}, StrUtil.split("a,b,c", ","));
    }

    @Test
    void splitKeepsTrailingEmpty() {
        assertArrayEquals(new String[]{"a", "", ""}, StrUtil.split("a,,", ","));
    }

    @Test
    void splitNullReturnsEmptyArray() {
        assertEquals(0, StrUtil.split(null, ",").length);
    }

    // ==================== 截取 ====================

    @Test
    void substringBeforeAndAfter() {
        assertEquals("a", StrUtil.substringBefore("a.b.c", "."));
        assertEquals("b.c", StrUtil.substringAfter("a.b.c", "."));
        assertEquals("a.b.c", StrUtil.substringBefore("a.b.c", "#"));
        assertEquals("", StrUtil.substringAfter("a.b.c", "#"));
    }

    @Test
    void substringBeforeAfterNullSafe() {
        assertNull(StrUtil.substringBefore(null, "."));
        assertNull(StrUtil.substringAfter(null, "."));
    }

    // ==================== 类型判断 ====================

    @Test
    void isNumeric() {
        assertTrue(StrUtil.isNumeric("12345"));
        assertFalse(StrUtil.isNumeric("12.3"));
        assertFalse(StrUtil.isNumeric("-1"));
        assertFalse(StrUtil.isNumeric(""));
        assertFalse(StrUtil.isNumeric(null));
    }

    // ==================== Unicode 修剪 ====================

    @Test
    void stripRemovesUnicodeWhitespace() {
        assertEquals("x", StrUtil.strip("  \u2028 x \u2029 "));
        assertNull(StrUtil.strip(null));
        assertEquals("", StrUtil.strip("\t\n"));
    }

    // ==================== 编解码 null 口径 ====================

    @Test
    void toBytesNullReturnsNull() {
        assertNull(StrUtil.toBytes(null));
        assertArrayEquals("hi".getBytes(StandardCharsets.UTF_8), StrUtil.toBytes("hi"));
    }

    @Test
    void fromBytesNullReturnsNull() {
        assertNull(StrUtil.fromBytes(null));
        assertEquals("hi", StrUtil.fromBytes("hi".getBytes(StandardCharsets.UTF_8)));
    }
}
