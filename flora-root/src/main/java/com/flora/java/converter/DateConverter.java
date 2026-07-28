package com.flora.java.converter;

import com.flora.java.Converter;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 日期时间转换器，覆盖 JDK 全部常用日期时间类型之间的互转。
 * <p>
 * 支持的来源/目标类型包括：{@link java.util.Date}、{@link LocalDate}、
 * {@link LocalDateTime}、{@link LocalTime}、{@link OffsetDateTime}、
 * {@link ZonedDateTime}、{@link Instant}、{@link Long}（毫秒时间戳）、{@link String}。
 * 字符串解析支持多种常见日期格式。
 * </p>
 * <p>
 * 内部以 {@link LocalDateTime} 为中枢：含时区的类型（{@code OffsetDateTime}/
 * {@code ZonedDateTime}/{@code Instant}/{@code Date}）按系统默认时区与中枢互转；
 * {@code LocalTime} 不含日期分量，与日期类型互转时以公元纪元首日（1970-01-01）补齐日期。
 * </p>
 */
public final class DateConverter implements Converter {

    private static final LocalDate EPOCH_DATE = LocalDate.of(1970, 1, 1);

    private static final String[] DEFAULT_PATTERNS = {
            "yyyy-MM-dd HH:mm:ss.SSS",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy/MM/dd HH:mm:ss.SSS",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy/MM/dd HH:mm",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm",
            "yyyyMMddHHmmss",
            "EEE, dd MMM yyyy HH:mm:ss z",
            "yyyy-MM-dd",
            "yyyy/MM/dd",
            "yyyyMMdd",
            "yyyy年MM月dd日 HH时mm分ss秒",
            "yyyy年MM月dd日",
            "yyyy年M月d日 H时m分s秒",
            "yyyy年M月d日"
    };

    private final String[] patterns;

    /**
     * 使用默认日期格式列表创建日期转换器。
     */
    public DateConverter() {
        this.patterns = DEFAULT_PATTERNS;
    }

    /**
     * 使用自定义日期格式列表创建日期转换器。
     *
     * @param patterns 日期格式模式列表
     */
    public DateConverter(String... patterns) {
        this.patterns = patterns.clone();
    }

    @Override
    public Collection<Class<?>> declareSourceTypes() {
        return List.of(Date.class, LocalDate.class, LocalDateTime.class, LocalTime.class,
                OffsetDateTime.class, ZonedDateTime.class, Instant.class, Long.class, String.class);
    }

    @Override
    public Collection<Class<?>> declareTargetTypes() {
        return List.of(Date.class, LocalDate.class, LocalDateTime.class, LocalTime.class,
                OffsetDateTime.class, ZonedDateTime.class, Instant.class, Long.class, String.class);
    }

    @Override
    public Object convert(Object from, Class<?> toType, Class<?> elementType) {
        if (from == null) {
            return null;
        }
        // LocalTime 不含日期分量，单独处理避免被错误地套用纪元日期
        if (from instanceof LocalTime t) {
            return convertFromLocalTime(t, toType);
        }

        LocalDateTime ldt = toLocalDateTime(from);

        if (toType == Date.class) {
            return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
        }
        if (toType == LocalDate.class) {
            return ldt.toLocalDate();
        }
        if (toType == LocalDateTime.class) {
            return ldt;
        }
        if (toType == LocalTime.class) {
            return ldt.toLocalTime();
        }
        if (toType == Instant.class) {
            return ldt.atZone(ZoneId.systemDefault()).toInstant();
        }
        if (toType == OffsetDateTime.class) {
            return ldt.atZone(ZoneId.systemDefault()).toOffsetDateTime();
        }
        if (toType == ZonedDateTime.class) {
            return ldt.atZone(ZoneId.systemDefault());
        }
        if (toType == Long.class) {
            return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        if (toType == String.class) {
            return ldt.toString();
        }
        throw new IllegalArgumentException("DateConverter 不支持目标类型: " + toType.getName());
    }

    /**
     * 将 {@link LocalTime} 转换为目标类型。
     * <p>仅支持含时间语义的目标；与日期相关的目标类型以纪元首日补齐日期。
     * {@code Long}/{@code Instant}/{@code OffsetDateTime}/{@code ZonedDateTime}
     * 没有对应的「当日时刻」语义，直接抛异常。</p>
     */
    private Object convertFromLocalTime(LocalTime t, Class<?> toType) {
        if (toType == LocalTime.class) {
            return t;
        }
        if (toType == String.class) {
            return t.toString();
        }
        if (toType == LocalDateTime.class) {
            return t.atDate(EPOCH_DATE);
        }
        if (toType == LocalDate.class) {
            return EPOCH_DATE;
        }
        if (toType == Date.class) {
            return Date.from(t.atDate(EPOCH_DATE).atZone(ZoneId.systemDefault()).toInstant());
        }
        throw new IllegalArgumentException("LocalTime 无法转换为 " + toType.getName() + "（时间类型不含日期分量）");
    }

    /**
     * 将任意类型的日期对象统一转换为 {@link LocalDateTime}。
     * 支持 {@link Date}、{@link LocalDateTime}、{@link LocalDate}、{@link LocalTime}、
     * {@link OffsetDateTime}、{@link ZonedDateTime}、{@link Instant}、{@link Long} 以及字符串格式。
     */
    private LocalDateTime toLocalDateTime(Object from) {
        if (from instanceof Date d) {
            return LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault());
        }
        if (from instanceof LocalDateTime ldt) {
            return ldt;
        }
        if (from instanceof LocalDate ld) {
            return ld.atStartOfDay();
        }
        if (from instanceof LocalTime lt) {
            return lt.atDate(EPOCH_DATE);
        }
        if (from instanceof Instant i) {
            return LocalDateTime.ofInstant(i, ZoneId.systemDefault());
        }
        if (from instanceof OffsetDateTime odt) {
            return odt.toLocalDateTime();
        }
        if (from instanceof ZonedDateTime zdt) {
            return zdt.toLocalDateTime();
        }
        if (from instanceof Long l) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(l), ZoneId.systemDefault());
        }
        String str = from.toString().trim();
        try {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(str)),
                    ZoneId.systemDefault());
        } catch (NumberFormatException ignored) {
        }
        Date parsed = parseToDate(str);
        return LocalDateTime.ofInstant(parsed.toInstant(), ZoneId.systemDefault());
    }

    /**
     * 使用配置的日期格式列表尝试解析字符串为 {@link Date}。
     * 依次尝试每个格式，返回第一个完全匹配的结果。
     *
     * @param str 待解析的日期字符串
     * @return 解析后的 Date 对象
     * @throws IllegalArgumentException 若所有格式均无法解析
     */
    private Date parseToDate(String str) {
        for (String pattern : patterns) {
            var sdf = new SimpleDateFormat(pattern, Locale.ENGLISH);
            ParsePosition pos = new ParsePosition(0);
            Date parsed = sdf.parse(str, pos);
            if (parsed != null && pos.getIndex() == str.length()) {
                return parsed;
            }
        }
        throw new IllegalArgumentException("无法将 '" + str + "' 转换为日期");
    }
}
