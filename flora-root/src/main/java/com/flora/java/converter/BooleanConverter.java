package com.flora.java.converter;

import java.util.Collection;
import java.util.List;

/**
 * 布尔值转换器，将任意对象转换为 {@link Boolean}。
 * <p>
 * 识别以下真值标记（不区分大小写）："true"、"yes"、"1"、"on"。
 * 对于数值类型，非零值视为 true；对于 {@link Boolean} 类型直接返回。
 * </p>
 */
public final class BooleanConverter implements Converter {

    private static final List<String> TRUE_TOKENS = List.of("true", "yes", "1", "on");

    @Override
    public Collection<Class<?>> declareSourceTypes() {
        return List.of(Object.class);
    }

    @Override
    public Collection<Class<?>> declareTargetTypes() {
        return List.of(Boolean.class);
    }

    @Override
    public Object convert(Object from, Class<?> toType, Class<?> elementType) {
        if (from == null) {
            return null;
        }
        assert toType == Boolean.class : "BooleanConverter 仅支持 Boolean 目标类型，实际: " + toType.getName();
        if (from instanceof Boolean b) {
            return b;
        }
        if (from instanceof Number n) {
            return n.doubleValue() != 0;
        }
        String str = from.toString().trim().toLowerCase();
        return TRUE_TOKENS.contains(str);
    }
}
