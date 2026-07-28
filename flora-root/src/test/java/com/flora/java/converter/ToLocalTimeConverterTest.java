package com.flora.java.converter;

import org.junit.jupiter.api.Test;

import com.flora.java.ConvertUtil;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToLocalTimeConverter 单测：验证多种来源类型 -> {@link LocalTime} 的时间分量提取。
 * 不抛“类型不支持”异常（非法输入字符串的解析失败除外，属输入校验，与 DateConverter 行为一致）。
 */
class ToLocalTimeConverterTest {

    private final ToLocalTimeConverter converter = new ToLocalTimeConverter();

    @Test
    void nullInputReturnsNull() {
        assertNull(converter.convert(null, LocalTime.class));
    }

    @Test
    void stringToLocalTime() {
        assertEquals(LocalTime.of(10, 30, 45), converter.convert("10:30:45", LocalTime.class));
    }

    @Test
    void dateTimeStringToLocalTimeExtractsTime() {
        assertEquals(LocalTime.of(12, 30), converter.convert("2025-03-04 12:30:00", LocalTime.class));
    }

    @Test
    void localDateTimeToLocalTime() {
        assertEquals(LocalTime.of(12, 30), converter.convert(LocalDateTime.of(2025, 3, 4, 12, 30), LocalTime.class));
    }

    @Test
    void localDateToLocalTimeIsMidnight() {
        assertEquals(LocalTime.MIDNIGHT, converter.convert(LocalDate.of(2025, 3, 4), LocalTime.class));
    }

    @Test
    void longToLocalTimeExtractsTime() {
        long epoch = LocalDateTime.of(2025, 3, 4, 12, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        assertEquals(LocalTime.of(12, 30), converter.convert(epoch, LocalTime.class));
    }

    @Test
    void instantToLocalTime() {
        Instant now = Instant.now();
        assertEquals(LocalTime.ofInstant(now, ZoneId.systemDefault()), converter.convert(now, LocalTime.class));
    }

    // ========== 经 ConvertUtil 门面（验证与 DateConverter 正交、无重复转换器异常） ==========

    @Test
    void viaConvertUtilStringToLocalTime() {
        assertEquals(LocalTime.of(10, 30, 45), ConvertUtil.convert(LocalTime.class, "10:30:45"));
    }

    @Test
    void viaConvertUtilLocalDateTimeToLocalTime() {
        assertEquals(LocalTime.of(12, 30),
                ConvertUtil.convert(LocalTime.class, LocalDateTime.of(2025, 3, 4, 12, 30)));
    }

    // ========== 非法输入（解析失败，属输入校验而非类型拒绝） ==========

    @Test
    void invalidTimeStringThrows() {
        assertThrows(IllegalArgumentException.class, () -> converter.convert("not-a-time", LocalTime.class));
    }
}
