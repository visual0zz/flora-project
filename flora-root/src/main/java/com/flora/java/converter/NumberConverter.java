package com.flora.java.converter;

import com.flora.java.Converter;

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
        // 数值类型之间的互转：先按数值（而非 toString 后解析）提取，
        // 避免 Double(5.7) -> Integer 之类场景因字符串解析失败而意外抛异常；
        // 仅在整数目标发生溢出时才抛出异常。
        if (from instanceof Number num) {
            return convertNumber(num, toType);
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

    /**
     * 数值类型之间的互转。整数目标（Integer/Short/Byte）在溢出时抛异常，
     * 其余目标按 {@link Number} 的数值直接转换。
     */
    private Object convertNumber(Number num, Class<?> toType) {
        if (toType == Integer.class) {
            long v = num.longValue();
            if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("数值 " + v + " 溢出 int");
            }
            return (int) v;
        }
        if (toType == Long.class) {
            return num.longValue();
        }
        if (toType == Double.class) {
            return num.doubleValue();
        }
        if (toType == Float.class) {
            return num.floatValue();
        }
        if (toType == Short.class) {
            long v = num.longValue();
            if (v < Short.MIN_VALUE || v > Short.MAX_VALUE) {
                throw new IllegalArgumentException("数值 " + v + " 溢出 short");
            }
            return (short) v;
        }
        if (toType == Byte.class) {
            long v = num.longValue();
            if (v < Byte.MIN_VALUE || v > Byte.MAX_VALUE) {
                throw new IllegalArgumentException("数值 " + v + " 溢出 byte");
            }
            return (byte) v;
        }
        if (toType == BigDecimal.class) {
            return toBigDecimal(num);
        }
        if (toType == BigInteger.class) {
            return toBigInteger(num);
        }
        throw new IllegalArgumentException("不支持的数值类型: " + toType.getName());
    }

    private BigDecimal toBigDecimal(Number num) {
        if (num instanceof BigDecimal bd) {
            return bd;
        }
        if (num instanceof BigInteger bi) {
            return new BigDecimal(bi);
        }
        if (num instanceof Double || num instanceof Float) {
            return BigDecimal.valueOf(num.doubleValue());
        }
        return BigDecimal.valueOf(num.longValue());
    }

    private BigInteger toBigInteger(Number num) {
        if (num instanceof BigInteger bi) {
            return bi;
        }
        if (num instanceof BigDecimal bd) {
            return bd.toBigInteger();
        }
        return BigInteger.valueOf(num.longValue());
    }
}
