package com.flora.java.converter;

import com.flora.java.Converter;

import java.util.Collection;
import java.util.List;

/**
 * 枚举转换器，将任意对象转换为枚举类型。
 * <p>
 * 按以下优先级匹配枚举常量：
 * <ol>
 *   <li>精确名称匹配（区分大小写）</li>
 *   <li>名称忽略大小写匹配</li>
 *   <li>解析字符串为 ordinal 值进行匹配</li>
 * </ol>
 * </p>
 */
public final class EnumConverter implements Converter {

    @Override
    public Collection<Class<?>> declareSourceTypes() {
        return List.of(Object.class);
    }

    @Override
    public Collection<Class<?>> declareTargetTypes() {
        return List.of(Enum.class);
    }

    @Override
    public Object convert(Object from, Class<?> toType, Class<?> elementType) {
        if (from == null) {
            return null;
        }
        if (!toType.isEnum()) {
            throw new IllegalArgumentException("EnumConverter 仅支持枚举目标类型: " + toType.getName());
        }
        @SuppressWarnings("unchecked")
        Class<? extends Enum<?>> enumClass = (Class<? extends Enum<?>>) toType;
        if (enumClass.isInstance(from)) {
            return enumClass.cast(from);
        }
        String str = from.toString().trim();
        for (Enum<?> constant : enumClass.getEnumConstants()) {
            if (constant.name().equals(str)) {
                return constant;
            }
        }
        for (Enum<?> constant : enumClass.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(str)) {
                return constant;
            }
        }
        try {
            int ordinal = Integer.parseInt(str);
            Enum<?>[] constants = enumClass.getEnumConstants();
            if (ordinal >= 0 && ordinal < constants.length) {
                return constants[ordinal];
            }
        } catch (NumberFormatException ignored) {
        }
        throw new IllegalArgumentException("无法将 '" + str + "' 转为 " + enumClass.getName());
    }
}
