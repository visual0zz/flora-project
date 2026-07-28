package com.flora.java.converter;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DateConverter 的完整单测。
 * 覆盖：6 种来源类型 -> 6 种目标类型、日期格式字符串解析、时间戳解析、异常情况。
 */
class DateConverterTest {

    private final DateConverter converter = new DateConverter();

    // ========== null 输入 ==========

    /**
     * 测试 null 输入返回 null。
     */
    @Test
    void nullInputReturnsNull() {
        assertNull(converter.convert(null, Date.class));
    }

    // ========== 字符串 -> 各目标类型 ==========

    /**
     * 测试日期字符串转 LocalDate。
     */
    @Test
    void dateStringToLocalDate() {
        assertEquals(LocalDate.of(2025, 3, 4), converter.convert("2025-03-04", LocalDate.class));
    }

    /**
     * 测试日期时间字符串转 LocalDateTime。
     */
    @Test
    void dateTimeString() {
        assertEquals(LocalDateTime.of(2025, 3, 4, 12, 30),
                converter.convert("2025-03-04 12:30:00", LocalDateTime.class));
    }

    /**
     * 测试 ISO 日期时间字符串转 LocalDateTime。
     */
    @Test
    void isoDateTimeString() {
        assertEquals(LocalDateTime.of(2025, 3, 4, 12, 30),
                converter.convert("2025-03-04T12:30:00", LocalDateTime.class));
    }

    /**
     * 测试时间戳字符串转 Date。
     */
    @Test
    void timestampStringToDate() {
        Date result = (Date) converter.convert("1700000000000", Date.class);
        assertEquals(1700000000000L, result.getTime());
    }

    /**
     * 测试 Long 时间戳转 Date。
     */
    @Test
    void epochLongToDate() {
        Date result = (Date) converter.convert(1700000000000L, Date.class);
        assertEquals(1700000000000L, result.getTime());
    }

    /**
     * 测试 LocalDate 转字符串。
     */
    @Test
    void dateStringToString() {
        Object result = converter.convert(LocalDate.of(2025, 3, 4), String.class);
        assertInstanceOf(String.class, result);
        assertTrue(((String) result).startsWith("2025-03-04"));
    }

    /**
     * 测试 LocalDateTime 转字符串。
     */
    @Test
    void localDateTimeToString() {
        Object result = converter.convert(LocalDateTime.of(2025, 3, 4, 12, 30), String.class);
        assertInstanceOf(String.class, result);
    }

    // ========== Date/LocalDateTime/LocalDate 互转 ==========

    /**
     * 测试 Date 转 LocalDateTime。
     */
    @Test
    void dateToLocalDateTime() {
        Date d = new Date();
        Object result = converter.convert(d, LocalDateTime.class);
        assertInstanceOf(LocalDateTime.class, result);
    }

    /**
     * 测试 LocalDateTime 转 Date。
     */
    @Test
    void localDateTimeToDate() {
        LocalDateTime ldt = LocalDateTime.of(2025, 6, 15, 10, 30);
        Object result = converter.convert(ldt, Date.class);
        assertInstanceOf(Date.class, result);
    }

    /**
     * 测试 LocalDate 转 Date。
     */
    @Test
    void localDateToDate() {
        LocalDate ld = LocalDate.of(2025, 12, 25);
        Object result = converter.convert(ld, Date.class);
        assertInstanceOf(Date.class, result);
    }

    /**
     * 测试 Date 转 LocalDate。
     */
    @Test
    void dateToLocalDate() {
        Date d = new Date();
        Object result = converter.convert(d, LocalDate.class);
        assertInstanceOf(LocalDate.class, result);
    }

    // ========== Long 类型互转 ==========

    /**
     * 测试 Date 转 Long（时间戳）。
     */
    @Test
    void dateToLong() {
        long now = System.currentTimeMillis();
        Date d = new Date(now);
        Object result = converter.convert(d, Long.class);
        assertEquals(now, result);
    }

    /**
     * 测试 Long 时间戳转 LocalDateTime。
     */
    @Test
    void longToLocalDateTime() {
        long epoch = 1700000000000L;
        Object result = converter.convert(epoch, LocalDateTime.class);
        assertInstanceOf(LocalDateTime.class, result);
    }

    // ========== 多种日期格式 ==========

    /**
     * 测试斜杠日期格式。
     */
    @Test
    void slashDateFormat() {
        Object result = converter.convert("2025/03/04", LocalDate.class);
        assertEquals(LocalDate.of(2025, 3, 4), result);
    }

    /**
     * 测试中文日期格式。
     */
    @Test
    void chineseDateFormat() {
        Object result = converter.convert("2025年03月04日", LocalDate.class);
        assertEquals(LocalDate.of(2025, 3, 4), result);
    }

    // ========== 异常情况 ==========

    /**
     * 测试不支持的目标类型抛出异常。
     */
    @Test
    void unsupportedTargetThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> converter.convert("2025-03-04", Integer.class));
    }

    /**
     * 测试无效日期字符串抛出异常。
     */
    @Test
    void invalidDateStringThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> converter.convert("not-a-date", LocalDate.class));
    }

    /**
     * 测试不支持的来源类型抛出异常。
     */
    @Test
    void unsupportedSourceTypeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> converter.convert(true, Date.class));
    }

    // ========== 自定义模式 ==========

    /**
     * 测试自定义日期模式。
     */
    @Test
    void customPattern() {
        DateConverter custom = new DateConverter("dd/MM/yyyy");
        Object result = custom.convert("04/03/2025", LocalDate.class);
        assertEquals(LocalDate.of(2025, 3, 4), result);
    }

    // ========== 新增时间类型：Instant / OffsetDateTime / ZonedDateTime ==========

    /**
     * 测试 Instant 转 LocalDateTime（按系统默认时区）。
     */
    @Test
    void instantToLocalDateTime() {
        Instant now = Instant.now();
        LocalDateTime result = (LocalDateTime) converter.convert(now, LocalDateTime.class);
        assertEquals(LocalDateTime.ofInstant(now, ZoneId.systemDefault()), result);
    }

    /**
     * 测试 LocalDateTime 转 Instant。
     */
    @Test
    void localDateTimeToInstant() {
        LocalDateTime ldt = LocalDateTime.of(2025, 6, 15, 10, 30);
        Instant result = (Instant) converter.convert(ldt, Instant.class);
        assertEquals(ldt.atZone(ZoneId.systemDefault()).toInstant(), result);
    }

    /**
     * 测试 OffsetDateTime 与 LocalDateTime 互转（保留墙钟时间）。
     */
    @Test
    void offsetDateTimeRoundTrip() {
        OffsetDateTime odt = OffsetDateTime.of(2025, 6, 15, 10, 30, 0, 0, ZoneId.of("+08:00").getRules().getOffset(LocalDateTime.of(2025, 6, 15, 10, 30)));
        LocalDateTime ldt = (LocalDateTime) converter.convert(odt, LocalDateTime.class);
        assertEquals(odt.toLocalDateTime(), ldt);
        OffsetDateTime back = (OffsetDateTime) converter.convert(ldt, OffsetDateTime.class);
        assertEquals(ldt.atZone(ZoneId.systemDefault()).toOffsetDateTime(), back);
    }

    /**
     * 测试 ZonedDateTime 与 LocalDateTime 互转。
     */
    @Test
    void zonedDateTimeRoundTrip() {
        ZonedDateTime zdt = ZonedDateTime.of(2025, 6, 15, 10, 30, 0, 0, ZoneId.of("America/New_York"));
        LocalDateTime ldt = (LocalDateTime) converter.convert(zdt, LocalDateTime.class);
        assertEquals(zdt.toLocalDateTime(), ldt);
        ZonedDateTime back = (ZonedDateTime) converter.convert(ldt, ZonedDateTime.class);
        assertEquals(ldt.atZone(ZoneId.systemDefault()), back);
    }

    /**
     * 测试 Date 转 Instant。
     */
    @Test
    void dateToInstant() {
        Date d = new Date(1700000000000L);
        Instant result = (Instant) converter.convert(d, Instant.class);
        assertEquals(d.toInstant(), result);
    }

    /**
     * 测试 Long 时间戳转 Instant。
     */
    @Test
    void longToInstant() {
        Instant result = (Instant) converter.convert(1700000000000L, Instant.class);
        assertEquals(Instant.ofEpochMilli(1700000000000L), result);
    }
}
