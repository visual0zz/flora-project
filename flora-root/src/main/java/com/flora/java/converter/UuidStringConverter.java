package com.flora.java.converter;

import com.flora.java.Converter;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * UUID 与字符串互转转换器。
 * <p>与 {@link UuidBytesConverter}（UUID↔byte[16]）拆分，使源/目标集合与
 * {@link ByteArrayConverter}（byte[]↔String, Hex）互不重叠，避免在
 * {@code byte[]→String} / {@code String→byte[]} 两对上触发「重复转换器」异常。</p>
 * <p>{@code UUID→String} / {@code String→UUID} 时本转换器来源比
 * {@link StringConverter}(Object) 更具体，按来源距离胜出；{@code byte[]↔String} 不归本转换器。</p>
 */
public final class UuidStringConverter implements Converter {

    @Override
    public Collection<Class<?>> declareSourceTypes() {
        return List.of(UUID.class, String.class);
    }

    @Override
    public Collection<Class<?>> declareTargetTypes() {
        return List.of(UUID.class, String.class);
    }

    @Override
    public Object convert(Object from, Class<?> toType, Class<?> elementType) {
        if (from == null) {
            return null;
        }
        if (toType == UUID.class) {
            if (from instanceof UUID u) {
                return u;
            }
            if (from instanceof String s) {
                try {
                    return UUID.fromString(s.trim());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("无法将字符串解析为 UUID: " + s, e);
                }
            }
            throw new IllegalArgumentException("UuidStringConverter 不支持的来源类型: " + from.getClass().getName());
        }
        if (toType == String.class) {
            if (from instanceof UUID u) {
                return u.toString();
            }
            if (from instanceof String s) {
                return s;
            }
            throw new IllegalArgumentException("UuidStringConverter 不支持的来源类型: " + from.getClass().getName());
        }
        throw new IllegalArgumentException("UuidStringConverter 不支持目标类型: " + toType.getName());
    }
}
