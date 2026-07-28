package com.flora.java.converter;

import com.flora.java.Converter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * 时间（{@link LocalTime}）专用转换器。
 * <p>与 {@link DateConverter} 正交：{@code DateConverter} 不处理 {@code LocalTime}，
 * 所有涉及 {@code LocalTime} 的转换（提取时间分量、或把时间分量组合进日期类型）都由本转换器承接，
 * 对外承诺的转换全部落地、不以异常拒绝类型。</p>
 * <p>约定：{@code LocalTime} 不含日期分量，组合进日期/时间类型时以公元纪元首日（1970-01-01）补齐日期；
 * 从含日期的类型提取 {@code LocalTime} 时取其当日时刻。字符串解析优先按时间格式，
 * 失败再回退到 {@link DateConverter} 的通用日期时间格式后取时刻。</p>
 */
public final class LocalTimeConverter implements Converter {

    private static final LocalDate EPOCH_DATE = LocalDate.of(1970, 1, 1);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final DateConverter dateConverter = new DateConverter();

    @Override
    public int declarePriority() {
        // 低于 DateConverter(0)：使 (日期类型, 日期类型) 这类重叠对被 DateConverter 选中，避免触发重复转换器异常
        return -1;
    }

    @Override
    public Collection<Class<?>> declareSourceTypes() {
        return List.of(LocalTime.class, String.class, LocalDateTime.class, LocalDate.class,
                Date.class, Instant.class, OffsetDateTime.class, ZonedDateTime.class, Long.class);
    }

    @Override
    public Collection<Class<?>> declareTargetTypes() {
        return List.of(LocalTime.class, String.class, LocalDateTime.class, LocalDate.class,
                Date.class, Instant.class, OffsetDateTime.class, ZonedDateTime.class, Long.class);
    }

    @Override
    public Object convert(Object from, Class<?> toType, Class<?> elementType) {
        if (from == null) {
            return null;
        }
        LocalTime t = toLocalTime(from);
        return fromLocalTime(t, toType);
    }

    /**
     * 把来源统一规约为 {@link LocalTime}（提取时间分量）。
     */
    private LocalTime toLocalTime(Object from) {
        if (from instanceof LocalTime t) {
            return t;
        }
        if (from instanceof LocalDateTime ldt) {
            return ldt.toLocalTime();
        }
        if (from instanceof LocalDate ld) {
            return LocalTime.MIDNIGHT;
        }
        if (from instanceof Instant i) {
            return LocalDateTime.ofInstant(i, ZoneId.systemDefault()).toLocalTime();
        }
        if (from instanceof OffsetDateTime odt) {
            return odt.toLocalTime();
        }
        if (from instanceof ZonedDateTime zdt) {
            return zdt.toLocalTime();
        }
        if (from instanceof Date d) {
            return LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault()).toLocalTime();
        }
        if (from instanceof Long l) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(l), ZoneId.systemDefault()).toLocalTime();
        }
        if (from instanceof String s) {
            return parseLocalTime(s);
        }
        throw new IllegalArgumentException("LocalTimeConverter 不支持的来源类型: " + from.getClass().getName());
    }

    /**
     * 解析时间字符串：先按 ISO/标准时间格式，失败再回退到通用日期时间格式后取时刻。
     */
    private LocalTime parseLocalTime(String s) {
        String str = s.trim();
        try {
            return LocalTime.parse(str);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalTime.parse(str, TIME_FORMATTER);
        } catch (DateTimeParseException ignored) {
        }
        LocalDateTime ldt = (LocalDateTime) dateConverter.convert(str, LocalDateTime.class);
        return ldt.toLocalTime();
    }

    /**
     * 把已规约的时间分量按目标类型落地。
     */
    private Object fromLocalTime(LocalTime t, Class<?> toType) {
        if (toType == LocalTime.class) {
            return t;
        }
        if (toType == String.class) {
            return t.format(TIME_FORMATTER);
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
        if (toType == Instant.class) {
            return t.atDate(EPOCH_DATE).atZone(ZoneId.systemDefault()).toInstant();
        }
        if (toType == OffsetDateTime.class) {
            return t.atDate(EPOCH_DATE).atZone(ZoneId.systemDefault()).toOffsetDateTime();
        }
        if (toType == ZonedDateTime.class) {
            return t.atDate(EPOCH_DATE).atZone(ZoneId.systemDefault());
        }
        if (toType == Long.class) {
            return t.atDate(EPOCH_DATE).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        throw new IllegalArgumentException("LocalTimeConverter 不支持目标类型: " + toType.getName());
    }
}
