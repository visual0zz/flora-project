package com.flora.codec;

import com.flora.java.ConvertUtil;
import com.flora.java.CustvertUtil;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
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
                () -> CustvertUtil.INSTANCE.convert("123", Integer.class));
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
}
