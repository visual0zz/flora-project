package com.flora.java.converter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;

/**
 * 数值转换器，将任意对象转换为各种数值类型。
 * <p>
 * 支持的数值目标类型包括：{@link Integer}、{@link Long}、{@link Double}、
 * {@link Float}、{@link Short}、{@link Byte}、{@link java.math.BigDecimal}、
 * {@link java.math.BigInteger}。
 * 转换方式为先将对象转为字符串再解析为目标数值类型。
 * </p>
 */
public final class NumberConverter implements Converter {

    private static final List<Class<?>> TO_TYPES = List.of(
            Integer.class,
            Long.class,
            Double.class,
            Float.class,
            Short.class,
            Byte.class,
            BigDecimal.class, BigInteger.class
    );

    @Override
    public Collection<Class<?>> declareSourceTypes() {
        return List.of(Object.class);
    }

    @Override
    public Collection<Class<?>> declareTargetTypes() {
        return TO_TYPES;
    }

    @Override
    public Object convert(Object from, Class<?> toType, Class<?> elementType) {
        if (from == null) {
            return null;
        }
        assert TO_TYPES.contains(toType) : "NumberConverter 仅支持数值目标类型，实际: " + toType.getName();
        if (toType.isInstance(from)) {
            return from;
        }
        String str = from.toString().trim();
        if (toType == Integer.class) {
            return Integer.parseInt(str);
        }
        if (toType == Long.class) {
            return Long.parseLong(str);
        }
        if (toType == Double.class) {
            return Double.parseDouble(str);
        }
        if (toType == Float.class) {
            return Float.parseFloat(str);
        }
        if (toType == Short.class) {
            return Short.parseShort(str);
        }
        if (toType == Byte.class) {
            return Byte.parseByte(str);
        }
        if (toType == BigDecimal.class) {
            return new BigDecimal(str);
        }
        if (toType == BigInteger.class) {
            return new BigInteger(str);
        }
        throw new IllegalArgumentException("不支持的数值类型: " + toType.getName());
    }
}
