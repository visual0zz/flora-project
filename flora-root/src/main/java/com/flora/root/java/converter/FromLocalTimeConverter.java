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
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * 时间（{@link LocalTime}）组合转换器：把 {@link LocalTime} 时间分量组合进多种日期/时间/数值/字符串类型。
 * <p>与 {@link ToLocalTimeConverter} 共同取代原 {@code LocalTimeConverter}：本转换器只负责“LocalTime -> 多类型”，
 * 来源是 {@link LocalTime} 且仅 {@link LocalTime}，因此与 {@link DateConverter}（不声明 LocalTime 来源）永不重叠，
 * 无需借助优先级即可避免重复转换器冲突。所有对外承诺的目标类型（9 种）都会被承接，不以异常拒绝类型。</p>
 * <p>约定：{@link LocalTime} 不含日期分量，组合进日期/时间类型时以公元纪元首日（1970-01-01）补齐日期；
 * 组合为字符串时按 {@code HH:mm:ss} 格式输出。</p>
 */
public final class FromLocalTimeConverter implements Converter {

    private static final LocalDate EPOCH_DATE = LocalDate.of(1970, 1, 1);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public Collection<Class<?>> declareSourceTypes() {
        return List.of(LocalTime.class);
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
        if (!(from instanceof LocalTime t)) {
            throw new IllegalArgumentException("FromLocalTimeConverter 仅接受 LocalTime 来源，实际: "
                    + from.getClass().getName());
        }
        return fromLocalTime(t, toType);
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
        throw new IllegalArgumentException("FromLocalTimeConverter 不支持目标类型: " + toType.getName());
    }
}
