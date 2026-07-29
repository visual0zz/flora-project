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

    // ==================== 空值/空白判断 ====================

    @Test
    void isEmpty() {
        assertTrue(StrUtil.isEmpty(null));
        assertTrue(StrUtil.isEmpty(""));
        assertFalse(StrUtil.isEmpty(" "));
        assertFalse(StrUtil.isEmpty("a"));
    }

    @Test
    void isNotEmpty() {
        assertFalse(StrUtil.isNotEmpty(null));
        assertFalse(StrUtil.isNotEmpty(""));
        assertTrue(StrUtil.isNotEmpty("a"));
    }

    @Test
    void isBlank() {
        assertTrue(StrUtil.isBlank(null));
        assertTrue(StrUtil.isBlank(""));
        assertTrue(StrUtil.isBlank(" \t\n"));
        assertFalse(StrUtil.isBlank(" a"));
    }

    @Test
    void isNotBlank() {
        assertFalse(StrUtil.isNotBlank(null));
        assertFalse(StrUtil.isNotBlank(""));
        assertTrue(StrUtil.isNotBlank("a"));
    }

    // ==================== 布尔判断 ====================

    @Test
    void isTrue() {
        assertTrue(StrUtil.isTrue("true"));
        assertTrue(StrUtil.isTrue("TRUE"));
        assertFalse(StrUtil.isTrue("yes"));
        assertFalse(StrUtil.isTrue(null));
    }

    @Test
    void isFalse() {
        assertTrue(StrUtil.isFalse("false"));
        assertTrue(StrUtil.isFalse("FALSE"));
        assertFalse(StrUtil.isFalse("no"));
        assertFalse(StrUtil.isFalse(null));
    }

    // ==================== 修剪 ====================

    @Test
    void trim() {
        assertNull(StrUtil.trim(null));
        assertEquals("", StrUtil.trim(""));
        assertEquals("x", StrUtil.trim(" x "));
    }

    @Test
    void trimToNull() {
        assertNull(StrUtil.trimToNull(null));
        assertNull(StrUtil.trimToNull(""));
        assertNull(StrUtil.trimToNull(" "));
        assertEquals("x", StrUtil.trimToNull(" x "));
    }

    @Test
    void trimToEmpty() {
        assertEquals("", StrUtil.trimToEmpty(null));
        assertEquals("", StrUtil.trimToEmpty("  "));
        assertEquals("x", StrUtil.trimToEmpty(" x "));
    }

    // ==================== 前缀后缀 ====================

    @Test
    void removePrefix() {
        assertEquals("world", StrUtil.removePrefix("hello world", "hello "));
        assertEquals("hello", StrUtil.removePrefix("hello", "x"));
        assertEquals("hello", StrUtil.removePrefix("hello", null));
        assertEquals("hello", StrUtil.removePrefix("hello", ""));
        assertNull(StrUtil.removePrefix(null, "a"));
    }

    @Test
    void removeSuffix() {
        assertEquals("hello", StrUtil.removeSuffix("hello world", " world"));
        assertEquals("hello", StrUtil.removeSuffix("hello", "x"));
        assertEquals("hello", StrUtil.removeSuffix("hello", null));
        assertEquals("hello", StrUtil.removeSuffix("hello", ""));
        assertNull(StrUtil.removeSuffix(null, "a"));
    }

    // ==================== 截取 substring ====================

    @Test
    void substringBegin() {
        assertEquals("cde", StrUtil.substring("abcde", 2));
        assertEquals("", StrUtil.substring("abcde", 10));
        assertEquals("e", StrUtil.substring("abcde", -1));
    }

    @Test
    void substringBeginEnd() {
        assertEquals("bc", StrUtil.substring("abcde", 1, 3));
        assertEquals("cde", StrUtil.substring("abcde", -3, 5));
        assertEquals("bcd", StrUtil.substring("abcde", 1, -1));
        assertEquals("", StrUtil.substring("abcde", 3, 3));
    }

    // ==================== left / right ====================

    @Test
    void left() {
        assertNull(StrUtil.left(null, 1));
        assertEquals("", StrUtil.left("abc", 0));
        assertEquals("", StrUtil.left("abc", -1));
        assertEquals("ab", StrUtil.left("abc", 2));
        assertEquals("abc", StrUtil.left("abc", 5));
    }

    @Test
    void right() {
        assertNull(StrUtil.right(null, 1));
        assertEquals("", StrUtil.right("abc", 0));
        assertEquals("", StrUtil.right("abc", -1));
        assertEquals("bc", StrUtil.right("abc", 2));
        assertEquals("abc", StrUtil.right("abc", 5));
    }

    // ==================== 填充 ====================

    @Test
    void padLeft() {
        assertNull(StrUtil.padLeft(null, 5, '0'));
        assertEquals("abc", StrUtil.padLeft("abc", 2, '0'));
        assertEquals("00abc", StrUtil.padLeft("abc", 5, '0'));
    }

    @Test
    void padRight() {
        assertNull(StrUtil.padRight(null, 5, '.'));
        assertEquals("abc", StrUtil.padRight("abc", 2, '.'));
        assertEquals("abc..", StrUtil.padRight("abc", 5, '.'));
    }

    // ==================== 重复 ====================

    @Test
    void repeat() {
        assertEquals("", StrUtil.repeat(null, 3));
        assertEquals("", StrUtil.repeat("a", 0));
        assertEquals("", StrUtil.repeat("a", -1));
        assertEquals("aaa", StrUtil.repeat("a", 3));
    }

    // ==================== 拼接 ====================

    @Test
    void joinVarargs() {
        assertEquals("a,b", StrUtil.join(",", "a", "b"));
        assertEquals("", StrUtil.join(",", (Object[]) null));
        assertEquals("", StrUtil.join(",", new Object[0]));
        assertEquals("a-null", StrUtil.join("-", "a", null));
        assertEquals("ab", StrUtil.join(null, "a", "b"));
    }

    @Test
    void joinIterable() {
        assertEquals("a,b", StrUtil.join(",", java.util.List.of("a", "b")));
        assertEquals("", StrUtil.join(",", (Iterable<String>) null));
        assertEquals("a-null", StrUtil.join("-", java.util.Arrays.asList("a", null)));
    }

    // ==================== 替换 ====================

    @Test
    void replaceChar() {
        assertNull(StrUtil.replace(null, 'a', 'b'));
        assertEquals("bbc", StrUtil.replace("abc", 'a', 'b'));
        assertEquals("abc", StrUtil.replace("abc", 'x', 'y'));
    }

    @Test
    void replaceString() {
        assertNull(StrUtil.replace(null, "a", "b"));
        assertEquals("hello", StrUtil.replace("hello", "x", "y"));
        assertEquals("abc", StrUtil.replace("abc", "", "x"));
        assertEquals("abc", StrUtil.replace("abc", null, "x"));
        assertEquals("xbx", StrUtil.replace("aba", "a", "x"));
    }

    // ==================== 大小写 ====================

    @Test
    void capitalize() {
        assertNull(StrUtil.capitalize(null));
        assertEquals("", StrUtil.capitalize(""));
        assertEquals("Abc", StrUtil.capitalize("abc"));
        assertEquals("ABC", StrUtil.capitalize("ABC"));
    }

    @Test
    void uncapitalize() {
        assertNull(StrUtil.uncapitalize(null));
        assertEquals("", StrUtil.uncapitalize(""));
        assertEquals("aBC", StrUtil.uncapitalize("ABC"));
        assertEquals("abc", StrUtil.uncapitalize("abc"));
    }

    // ==================== 反转 ====================

    @Test
    void reverse() {
        assertNull(StrUtil.reverse(null));
        assertEquals("", StrUtil.reverse(""));
        assertEquals("cba", StrUtil.reverse("abc"));
    }

    // ==================== 截断 ====================

    @Test
    void truncate() {
        assertNull(StrUtil.truncate(null, 3, ".."));
        assertEquals("abc", StrUtil.truncate("abc", 5, ".."));
        assertEquals("abc..", StrUtil.truncate("abcdef", 5, ".."));
        assertEquals("abc", StrUtil.truncate("abcdef", 3, null));
    }

    // ==================== 默认值 ====================

    @Test
    void defaultIfNull() {
        assertEquals("d", StrUtil.defaultIfNull(null, "d"));
        assertEquals("a", StrUtil.defaultIfNull("a", "d"));
    }

    @Test
    void defaultIfEmpty() {
        assertEquals("d", StrUtil.defaultIfEmpty(null, "d"));
        assertEquals("d", StrUtil.defaultIfEmpty("", "d"));
        assertEquals("a", StrUtil.defaultIfEmpty("a", "d"));
    }

    @Test
    void defaultIfBlank() {
        assertEquals("d", StrUtil.defaultIfBlank(null, "d"));
        assertEquals("d", StrUtil.defaultIfBlank("", "d"));
        assertEquals("d", StrUtil.defaultIfBlank(" ", "d"));
        assertEquals("a", StrUtil.defaultIfBlank("a", "d"));
    }
}
