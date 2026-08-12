package com.flora.root.java.converter;

import com.flora.root.java.Converter;

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
 * 时间（{@link LocalTime}）提取转换器：把多种日期/时间/数值/字符串类型统一提取为 {@link LocalTime} 时间分量。
 * <p>与 {@link FromLocalTimeConverter} 共同取代原 {@code LocalTimeConverter}：本转换器只负责“多类型 -> LocalTime”，
 * 目标是 {@link LocalTime} 且仅 {@link LocalTime}，因此与 {@link DateConverter}（不含 LocalTime）永不重叠，
 * 无需借助优先级即可避免重复转换器冲突。所有对外承诺的来源类型（8 种）都会被承接，不以异常拒绝类型。</p>
 * <p>约定：从含日期的类型提取 {@link LocalTime} 时取其当日时刻；字符串解析优先按时间格式，
 * 失败再回退到 {@link DateConverter} 的通用日期时间格式后取时刻（与 DateConverter 行为一致的输入校验）。</p>
 */
public final class ToLocalTimeConverter implements Converter {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final DateConverter dateConverter = new DateConverter();

    @Override
    public Collection<Class<?>> declareSourceTypes() {
        return List.of(String.class, LocalDateTime.class, LocalDate.class,
                Date.class, Instant.class, OffsetDateTime.class, ZonedDateTime.class, Long.class);
    }

    @Override
    public Collection<Class<?>> declareTargetTypes() {
        return List.of(LocalTime.class);
    }

    @Override
    public Object convert(Object from, Class<?> toType, Class<?> elementType) {
        if (from == null) {
            return null;
        }
        return toLocalTime(from);
    }

    /**
     * 把来源统一规约为 {@link LocalTime}（提取时间分量）。
     */
    private LocalTime toLocalTime(Object from) {
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
        throw new IllegalArgumentException("ToLocalTimeConverter 不支持的来源类型: " + from.getClass().getName());
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
}
