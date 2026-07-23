package com.flora.java.converter;

import com.flora.java.Converter;

import java.util.Collection;
import java.util.List;

/**
 * 字符串转换器，将任意对象转换为 {@link String}。
 * <p>
 * 对于数组类型，将元素逐个装箱后使用 {@link java.util.Arrays#deepToString(Object[])} 转换；
 * 其他对象则直接调用其 {@code toString()} 方法。
 * </p>
 */
public final class StringConverter implements Converter {

    @Override
    public Collection<Class<?>> declareSourceTypes() {
        return List.of(Object.class);
    }

    @Override
    public Collection<Class<?>> declareTargetTypes() {
        return List.of(String.class);
    }

    @Override
    public Object convert(Object from, Class<?> toType, Class<?> elementType) {
        if (from == null) {
            return null;
        }
        assert toType == String.class : "StringConverter 仅支持 String 目标类型，实际: " + toType.getName();
        if (from instanceof String s) {
            return s;
        }
        if (from.getClass().isArray()) {
            // 处理基本类型数组和对象数组
            int len = java.lang.reflect.Array.getLength(from);
            Object[] boxed = new Object[len];
            for (int i = 0; i < len; i++) {
                boxed[i] = java.lang.reflect.Array.get(from, i);
            }
            return java.util.Arrays.deepToString(boxed);
        }
        return from.toString();
    }
}
