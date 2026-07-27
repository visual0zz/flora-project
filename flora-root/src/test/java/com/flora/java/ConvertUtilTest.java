package com.flora.java;

import com.flora.java.ConvertUtil;
import com.flora.java.CustvertUtil;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConvertUtil 转换工具类的单元测试。
 * 测试基本类型互转、标识/向上转型、null 处理、异常情况、数组/集合元素转换及 SPI 自定义转换器。
 */
class ConvertUtilTest {

    // ==================== 基本类型互转 ====================

    /**
     * 测试 int 转 String。
     */
    @Test
    void intToString() {
        assertEquals("123", ConvertUtil.convert(String.class, 123));
    }

    /**
     * 测试 String 转 Integer。
     */
    @Test
    void stringToInt() {
        assertEquals(Integer.valueOf(123), ConvertUtil.convert(Integer.class, "123"));
    }

    /**
     * 测试 String 转 BigDecimal。
     */
    @Test
    void stringToBigDecimal() {
        assertEquals(new BigDecimal("1.5"), ConvertUtil.convert(BigDecimal.class, "1.5"));
    }

    /**
     * 测试 String 转 BigInteger。
     */
    @Test
    void stringToBigInteger() {
        assertEquals(BigInteger.valueOf(123), ConvertUtil.convert(BigInteger.class, "123"));
    }

    /**
     * 测试 String 转日期类型（LocalDate 和 Date）。
     */
    @Test
    void stringToDate() {
        assertEquals(LocalDate.of(2025, 3, 4), ConvertUtil.convert(LocalDate.class, "2025-03-04"));
        assertInstanceOf(Date.class, ConvertUtil.convert(Date.class, "2025-03-04"));
    }

    // ==================== Identity / Upcast ====================

    /**
     * 测试相同类型转换返回原对象。
     */
    @Test
    void identityReturnsSameValue() {
        Integer v = 42;
        assertSame(v, ConvertUtil.convert(Integer.class, v));
    }

    /**
     * 测试向上转型（Integer -> Number）。
     */
    @Test
    void upcastToNumber() {
        assertEquals(Integer.valueOf(42), ConvertUtil.convert(Number.class, 42));
    }

    // ==================== null 处理 ====================

    /**
     * 测试 null 输入返回 null。
     */
    @Test
    void nullReturnsNull() {
        Object nullVal = null;
        assertNull(ConvertUtil.convert(String.class, nullVal));
        assertNull(ConvertUtil.convert(Integer.class, nullVal));
    }

    // ==================== 异常情况 ====================

    /**
     * 测试找不到转换器时抛出异常。
     */
    @Test
    void noConverterThrows() {
        assertThrows(IllegalArgumentException.class, () -> ConvertUtil.convert(Thread.class, "x"));
    }

    /**
     * 测试非法转换时抛出异常。
     */
    @Test
    void invalidConversionThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> ConvertUtil.convert(Integer.class, "not-a-number"));
    }

    /**
     * 测试 convertQuietly 在转换失败时返回默认值。
     */
    @Test
    void convertQuietlyReturnsDefaultOnFailure() {
        assertEquals(Integer.valueOf(-1), ConvertUtil.convertQuietly(Integer.class, "not-a-number", -1));
    }

    /**
     * 测试 convertQuietly 在转换成功时返回正确值。
     */
    @Test
    void convertQuietlyReturnsValueOnSuccess() {
        assertEquals(Integer.valueOf(42),
                ConvertUtil.convertQuietly(Integer.class, "42", -1));
    }

    /**
     * 测试 convertQuietly 在 null 输入时返回 null，不触发默认值。
     */
    @Test
    void convertQuietlyReturnsNullDefaultOnNullInput() {
        assertNull(ConvertUtil.convertQuietly(Integer.class, (Object) null, 42));
    }

    // ==================== 数组元素转换 ====================

    /**
     * 测试 String[] 到 Integer[] 的数组元素转换。
     */
    @Test
    void arrayElementConversionThroughFacade() {
        Integer[] result = ConvertUtil.convert(Integer[].class, new String[]{"1", "2", "3"});
        assertArrayEquals(new Integer[]{1, 2, 3}, result);
    }

    /**
     * 测试混合类型数组到 Integer[] 的转换。
     */
    @Test
    void arrayElementConversionMixedToInt() {
        Integer[] result = ConvertUtil.convert(Integer[].class, new Object[]{1, "2", 3L});
        assertArrayEquals(new Integer[]{1, 2, 3}, result);
    }

    // ==================== 集合元素转换 ====================

    /**
     * 测试集合元素转换（List<String> -> List<Integer>）。
     */
    @Test
    void collectionElementConversionViaConvertElements() {
        List<?> result = ConvertUtil.convertElements(List.class, List.of("1", "2", "3"), Integer.class);
        assertEquals(List.of(1, 2, 3), result);
    }

    /**
     * 测试集合元素转换到 Set。
     */
    @Test
    void collectionElementConversionToSetViaConvertElements() {
        Set<?> result = ConvertUtil.convertElements(Set.class, List.of("1", "2", "3"), Integer.class);
        assertEquals(Set.of(1, 2, 3), result);
    }

    /**
     * 测试未指定元素类型时回退到原值。
     */
    @Test
    void convertElementsWithoutElementTypeFallsBack() {
        List<?> result = ConvertUtil.convertElements(List.class, List.of("1", "2", "3"), null);
        assertEquals(List.of("1", "2", "3"), result);
    }

    /**
     * 测试 null 值输入时返回 null。
     */
    @Test
    void convertElementsWithNullValueReturnsNull() {
        Object nullVal = null;
        assertNull(ConvertUtil.convertElements(List.class, nullVal, Integer.class));
    }

    // ==================== CustvertUtil（仅 SPI） ====================

    /**
     * 测试 CustvertUtil 仅加载 SPI 转换器，无内置转换器时转换失败。
     */
    @Test
    void customOnlyUsesNoBuiltIns() {
        assertThrows(IllegalArgumentException.class,
                () -> CustvertUtil.INSTANCE.convertElements("123", Integer.class, null));
    }

    /**
     * 测试无内置转换器时 identity 和 upcast 仍可通过 NoopConverter 工作。
     */
    @Test
    void customOnlyIdentityWorks() {
        Object v = "hello";
        assertSame(v, CustvertUtil.INSTANCE.convertQuietly("hello", String.class, (String) null));
    }

    /**
     * 测试无内置转换器时转换失败返回默认值。
     */
    @Test
    void customOnlyReturnsDefaultWhenFails() {
        assertEquals("fallback",
                CustvertUtil.INSTANCE.convertQuietly(42, String.class, "fallback"));
    }

    // ==================== targetType 边界 ====================

    /**
     * 测试 targetType 为 null 时抛出 NullPointerException。
     */
    @Test
    void nullTargetTypeThrowsNpe() {
        assertThrows(NullPointerException.class, () -> ConvertUtil.convert((Class<?>) null, "x"));
    }

    /**
     * 测试原始类型目标（如 int.class）无对应转换器时抛出异常。
     * 转换器仅支持装箱类型，原始类型目标不可转换。
     */
    @Test
    void primitiveTargetTypeThrows() {
        assertThrows(IllegalArgumentException.class, () -> ConvertUtil.convert(int.class, "5"));
    }

    /**
     * 测试目标类型为 Object 时原样返回（NoopConverter 处理向上转型）。
     */
    @Test
    void objectTargetReturnsValueAsIs() {
        String v = "hello";
        assertSame(v, ConvertUtil.convert(Object.class, v));
        assertSame(Integer.valueOf(7), ConvertUtil.convert(Object.class, 7));
    }

    // ==================== Boolean 边界 ====================

    /**
     * 测试布尔真值 token：true/yes/1/on，忽略大小写与前后空格。
     */
    @Test
    void booleanTruthyTokens() {
        assertTrue(ConvertUtil.convert(Boolean.class, "true"));
        assertTrue(ConvertUtil.convert(Boolean.class, "YES"));
        assertTrue(ConvertUtil.convert(Boolean.class, "1"));
        assertTrue(ConvertUtil.convert(Boolean.class, "on"));
        assertTrue(ConvertUtil.convert(Boolean.class, "  On  "));
    }

    /**
     * 测试布尔假值 token 及任意非匹配串。
     */
    @Test
    void booleanFalsyTokens() {
        assertFalse(ConvertUtil.convert(Boolean.class, "false"));
        assertFalse(ConvertUtil.convert(Boolean.class, "no"));
        assertFalse(ConvertUtil.convert(Boolean.class, "anything-else"));
    }

    /**
     * 测试数值到布尔：非零为 true，零为 false。
     */
    @Test
    void booleanFromNumeric() {
        assertTrue(ConvertUtil.convert(Boolean.class, 5));
        assertFalse(ConvertUtil.convert(Boolean.class, 0));
        assertTrue(ConvertUtil.convert(Boolean.class, 3.5));
    }

    /**
     * 测试 Boolean 源值为 identity。
     */
    @Test
    void booleanIdentity() {
        assertSame(Boolean.TRUE, ConvertUtil.convert(Boolean.class, true));
    }

    // ==================== Number 边界 ====================

    /**
     * 测试所有数值目标类型的字符串转换。
     */
    @Test
    void numberAllTypes() {
        assertEquals(Long.valueOf(5L), ConvertUtil.convert(Long.class, "5"));
        assertEquals(Double.valueOf(1.5), ConvertUtil.convert(Double.class, "1.5"));
        assertEquals(Float.valueOf(1.5f), ConvertUtil.convert(Float.class, "1.5"));
        assertEquals(Short.valueOf((short) 5), ConvertUtil.convert(Short.class, "5"));
        assertEquals(Byte.valueOf((byte) 5), ConvertUtil.convert(Byte.class, "5"));
        assertEquals(new BigDecimal("1.5"), ConvertUtil.convert(BigDecimal.class, "1.5"));
        assertEquals(BigInteger.valueOf(123), ConvertUtil.convert(BigInteger.class, "123"));
    }

    /**
     * 测试负数与前后空格被裁剪。
     */
    @Test
    void numberNegativeAndWhitespace() {
        assertEquals(Integer.valueOf(-42), ConvertUtil.convert(Integer.class, " -42 "));
        assertEquals(Integer.valueOf(-42), ConvertUtil.convert(Integer.class, -42));
        assertEquals(Long.valueOf(-9L), ConvertUtil.convert(Long.class, -9L));
    }

    /**
     * 测试跨数值类型（Long -> Integer）经 toString 再解析。
     */
    @Test
    void numberCrossTypeLongToInt() {
        assertEquals(Integer.valueOf(42), ConvertUtil.convert(Integer.class, 42L));
    }

    /**
     * 测试数值越界抛 NumberFormatException（Byte/Short 范围外）。
     */
    @Test
    void numberOutOfRangeThrows() {
        assertThrows(NumberFormatException.class, () -> ConvertUtil.convert(Byte.class, "128"));
        assertThrows(NumberFormatException.class, () -> ConvertUtil.convert(Short.class, "40000"));
    }

    /**
     * 测试非法数值格式抛 NumberFormatException。
     */
    @Test
    void numberInvalidFormatThrows() {
        assertThrows(NumberFormatException.class, () -> ConvertUtil.convert(Integer.class, "abc"));
    }

    // ==================== Enum 边界 ====================

    private enum Color {RED, GREEN, BLUE}

    /**
     * 测试枚举：精确名与忽略大小写匹配。
     */
    @Test
    void enumExactAndCaseInsensitive() {
        assertSame(Color.RED, ConvertUtil.convert(Color.class, "RED"));
        assertSame(Color.RED, ConvertUtil.convert(Color.class, "red"));
    }

    /**
     * 测试枚举按 ordinal 匹配（数值与字符串两种入参）。
     */
    @Test
    void enumByOrdinal() {
        assertSame(Color.GREEN, ConvertUtil.convert(Color.class, 1));
        assertSame(Color.GREEN, ConvertUtil.convert(Color.class, "1"));
    }

    /**
     * 测试非法枚举名抛 IllegalArgumentException。
     */
    @Test
    void enumInvalidThrows() {
        assertThrows(IllegalArgumentException.class, () -> ConvertUtil.convert(Color.class, "PURPLE"));
    }

    // ==================== Date 边界 ====================

    /**
     * 测试 LocalDate 原样往返。
     */
    @Test
    void dateLocalDateRoundTrip() {
        LocalDate d = LocalDate.of(2025, 3, 4);
        assertEquals(d, ConvertUtil.convert(LocalDate.class, d));
    }

    /**
     * 测试 LocalDate 转 String 与 Long（毫秒时间戳）。
     */
    @Test
    void dateToStringAndLong() {
        LocalDate d = LocalDate.of(2025, 3, 4);
        assertInstanceOf(String.class, ConvertUtil.convert(String.class, d));
        assertInstanceOf(Long.class, ConvertUtil.convert(Long.class, d));
    }

    /**
     * 测试非法日期字符串抛 IllegalArgumentException。
     */
    @Test
    void dateInvalidStringThrows() {
        assertThrows(IllegalArgumentException.class, () -> ConvertUtil.convert(LocalDate.class, "not-a-date"));
    }

    // ==================== Array 边界 ====================

    /**
     * 测试从 Collection 转换为数组并转换元素。
     */
    @Test
    void arrayFromCollection() {
        Integer[] r = ConvertUtil.convert(Integer[].class, List.of("1", "2"));
        assertArrayEquals(new Integer[]{1, 2}, r);
    }

    /**
     * 测试单值转换为长度为 1 的数组。
     */
    @Test
    void arraySingleValue() {
        String[] r = ConvertUtil.convert(String[].class, "x");
        assertArrayEquals(new String[]{"x"}, r);
    }

    /**
     * 测试原始类型数组目标（元素为原始类型）无转换器时抛异常。
     */
    @Test
    void arrayPrimitiveTargetThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> ConvertUtil.convert(int[].class, new String[]{"1"}));
    }

    /**
     * 测试数组元素为 null 时保留为 null（ConvertUtil 对 null 元素返回 null，不抛异常）。
     */
    @Test
    void arrayPreservesNullElement() {
        Integer[] r = ConvertUtil.convert(Integer[].class, new String[]{"1", null, "3"});
        assertArrayEquals(new Integer[]{1, null, 3}, r);
    }

    // ==================== String 来自数组 ====================

    /**
     * 测试原始类型数组转 String 使用 Arrays.deepToString。
     */
    @Test
    void stringFromPrimitiveArrayUsesDeepToString() {
        assertEquals("[1, 2, 3]", ConvertUtil.convert(String.class, new int[]{1, 2, 3}));
    }

    // ==================== Optional 边界 ====================

    /**
     * 测试任意值被包装为 Optional。
     */
    @Test
    void optionalWrapsValue() {
        assertEquals(Optional.of(5), ConvertUtil.convert(Optional.class, 5));
    }

    /**
     * 测试 null 输入时，门面在到达 OptionalConverter 前即短路返回 null
     * （与「null 入参 -> null 出参」的整体契约一致，而非 Optional.empty()）。
     */
    @Test
    void optionalWrapsNullAsNull() {
        assertNull(ConvertUtil.convert(Optional.class, null));
    }

    // ==================== convertQuietly 边界 ====================

    /**
     * 测试 convertQuietly 在 targetType 为 null（抛 NPE）时仍捕获异常并返回默认值。
     */
    @Test
    void convertQuietlyNullTargetReturnsDefault() {
        assertEquals("d", ConvertUtil.convertQuietly((Class<String>) null, "x", "d"));
    }
}
