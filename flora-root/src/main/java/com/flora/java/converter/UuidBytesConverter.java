package com.flora.java.converter;

import com.flora.java.Converter;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * UUID 与 16 字节数组互转转换器，采用 RFC 4122 大端（网络字节序）布局：
 * 前 8 字节 = {@link UUID#getMostSignificantBits()} 大端，后 8 字节 = {@link UUID#getLeastSignificantBits()} 大端。
 * <p>大端符合 RFC 4122 / Postgres / Python {@code uuid} 的 UUID 字节约定，便于落库与跨系统互通；
 * 本库 {@code BytesUtil.long2bytes} 为小端数值编码，二者领域不同，此为有意取舍。</p>
 * <p>与 {@link UuidStringConverter}（UUID↔String）拆分，且源/目标仅含 {@code UUID}/{@code byte[]}，
 * 不与 {@link ByteArrayConverter}（byte[]↔String, Hex）抢源/目标，{@code byte[]↔String} 仍唯一归后者。</p>
 */
public final class UuidBytesConverter implements Converter {

    private static final int UUID_BYTES = 16;

    @Override
    public Collection<Class<?>> declareSourceTypes() {
        return List.of(UUID.class, byte[].class);
    }

    @Override
    public Collection<Class<?>> declareTargetTypes() {
        return List.of(UUID.class, byte[].class);
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
            if (from instanceof byte[] b) {
                if (b.length != UUID_BYTES) {
                    throw new IllegalArgumentException("UUID 字节数组长度必须为 " + UUID_BYTES + "，实际: " + b.length);
                }
                long msb = 0;
                long lsb = 0;
                for (int i = 0; i < 8; i++) {
                    msb = (msb << 8) | (b[i] & 0xff);
                }
                for (int i = 8; i < UUID_BYTES; i++) {
                    lsb = (lsb << 8) | (b[i] & 0xff);
                }
                return new UUID(msb, lsb);
            }
            throw new IllegalArgumentException("UuidBytesConverter 不支持的来源类型: " + from.getClass().getName());
        }
        if (toType == byte[].class) {
            if (from instanceof UUID u) {
                byte[] out = new byte[UUID_BYTES];
                long msb = u.getMostSignificantBits();
                long lsb = u.getLeastSignificantBits();
                for (int i = 0; i < 8; i++) {
                    out[i] = (byte) ((msb >>> (8 * (7 - i))) & 0xff);
                }
                for (int i = 0; i < 8; i++) {
                    out[8 + i] = (byte) ((lsb >>> (8 * (7 - i))) & 0xff);
                }
                return out;
            }
            if (from instanceof byte[] b) {
                return b;
            }
            throw new IllegalArgumentException("UuidBytesConverter 不支持的来源类型: " + from.getClass().getName());
        }
        throw new IllegalArgumentException("UuidBytesConverter 不支持目标类型: " + toType.getName());
    }
}
