package com.flora.java.converter;

import com.flora.codec.HexUtil;
import com.flora.java.Converter;

import java.util.Collection;
import java.util.List;

/**
 * 字节数组转换器：在 {@code byte[]} 与 {@link String}（十六进制）之间互转。
 * <p>与 {@link com.flora.java.BytesUtil} 的 hex 编解码行为一致（小写 hex），
 * 但接入统一转换体系，使 {@code ConvertUtil.convert(String.class, bytes)} /
 * {@code ConvertUtil.convert(byte[].class, "...")} 也可用。</p>
 * <p>仅采用单一 Hex 格式（不做 Base64、不做构造参数切换）：{@code byte[]↔String} 只能注册一个实例，
 * 若同时注册 Hex 与 Base64 两版会因来源/目标集合完全相同而触发「重复转换器」异常。
 * 与 {@link UuidStringConverter}/{@link UuidBytesConverter} 通过源/目标集合不相交避免冲突：
 * 本转换器不认领 {@code UUID}，{@code byte[]↔String} 仅由本转换器独占。</p>
 */
public final class ByteArrayConverter implements Converter {

    @Override
    public Collection<Class<?>> declareSourceTypes() {
        return List.of(byte[].class, String.class);
    }

    @Override
    public Collection<Class<?>> declareTargetTypes() {
        return List.of(byte[].class, String.class);
    }

    @Override
    public Object convert(Object from, Class<?> toType, Class<?> elementType) {
        if (from == null) {
            return null;
        }
        if (toType == String.class) {
            if (from instanceof byte[] b) {
                return HexUtil.encodeHex(b);
            }
            if (from instanceof String s) {
                return s;
            }
            throw new IllegalArgumentException("ByteArrayConverter 不支持的来源类型: " + from.getClass().getName());
        }
        if (toType == byte[].class) {
            if (from instanceof String s) {
                try {
                    return HexUtil.decodeHex(s);
                } catch (RuntimeException e) {
                    throw new IllegalArgumentException("无法将字符串解析为十六进制字节数组: " + s, e);
                }
            }
            if (from instanceof byte[] b) {
                return b;
            }
            throw new IllegalArgumentException("ByteArrayConverter 不支持的来源类型: " + from.getClass().getName());
        }
        throw new IllegalArgumentException("ByteArrayConverter 不支持目标类型: " + toType.getName());
    }
}
