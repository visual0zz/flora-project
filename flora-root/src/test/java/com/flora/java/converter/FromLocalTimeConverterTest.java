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
 * FromLocalTimeConverter 单测：验证 {@link LocalTime} -> 多种目标类型的组合落地。
 * 不抛“类型不支持”异常（类型拒绝）。
 */
class FromLocalTimeConverterTest {

    private final FromLocalTimeConverter converter = new FromLocalTimeConverter();

    @Test
    void nullInputReturnsNull() {
        assertNull(converter.convert(null, LocalTime.class));
    }

    @Test
    void localTimeToLocalTime() {
        LocalTime t = LocalTime.of(10, 30, 45);
        assertSame(t, converter.convert(t, LocalTime.class));
    }

    @Test
    void localTimeToString() {
        assertEquals("10:30:45", converter.convert(LocalTime.of(10, 30, 45), String.class));
    }

    @Test
    void localTimeToLocalDateTimeUsesEpochDate() {
        LocalDateTime result = (LocalDateTime) converter.convert(LocalTime.of(10, 30), LocalDateTime.class);
        assertEquals(LocalDateTime.of(1970, 1, 1, 10, 30), result);
    }

    @Test
    void localTimeToLocalDateUsesEpochDate() {
        assertEquals(LocalDate.of(1970, 1, 1), converter.convert(LocalTime.of(10, 30), LocalDate.class));
    }

    @Test
    void localTimeToDate() {
        Date d = (Date) converter.convert(LocalTime.of(10, 30), Date.class);
        assertEquals(LocalDateTime.of(1970, 1, 1, 10, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                d.getTime());
    }

    @Test
    void localTimeToLong() {
        long ms = (long) converter.convert(LocalTime.of(10, 30), Long.class);
        assertEquals(LocalDateTime.of(1970, 1, 1, 10, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), ms);
    }

    @Test
    void localTimeToInstant() {
        Instant i = (Instant) converter.convert(LocalTime.of(10, 30), Instant.class);
        assertEquals(LocalDateTime.of(1970, 1, 1, 10, 30).atZone(ZoneId.systemDefault()).toInstant(), i);
    }

    // ========== 经 ConvertUtil 门面（验证与 StringConverter/NumberConverter 距离判别、无重复转换器异常） ==========

    @Test
    void viaConvertUtilLocalTimeToString() {
        assertEquals("10:30:45", ConvertUtil.convert(String.class, LocalTime.of(10, 30, 45)));
    }
}
