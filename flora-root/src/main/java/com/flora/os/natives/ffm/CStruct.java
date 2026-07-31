package com.flora.os.natives.ffm;

import java.lang.foreign.*;
import java.nio.charset.StandardCharsets;

/**
 * C 结构体实例，由 {@link CStructType#create(Arena)} 创建。
 * <p>提供类型安全的字段读写方法，无需手动计算偏移。</p>
 */
public final class CStruct implements AutoCloseable {

    private final MemorySegment segment;
    private final CStructType type;

    CStruct(MemorySegment segment, CStructType type) {
        this.segment = segment;
        this.type = type;
    }

    // ====== 读取 ======

    public int     getInt(String f)     { return seg().get(CStructType.INT32, off(f)); }
    public long    getLong(String f)    { return seg().get(CStructType.INT64, off(f)); }
    public float   getFloat(String f)   { return seg().get(CStructType.FLOAT, off(f)); }
    public double  getDouble(String f)  { return seg().get(CStructType.DOUBLE, off(f)); }
    public short   getShort(String f)   { return seg().get(CStructType.INT16, off(f)); }
    public byte    getByte(String f)    { return seg().get(CStructType.INT8, off(f)); }
    public MemorySegment getPtr(String f) { return seg().get(CStructType.PTR, off(f)); }

    public String getString(String f) {
        var ptr = seg().get(CStructType.PTR, off(f));
        return (ptr == null || ptr.equals(MemorySegment.NULL)) ? null
                : ptr.reinterpret(Long.MAX_VALUE).getString(0);
    }

    // ====== 写入 ======

    public CStruct setInt(String f, int v)       { seg().set(CStructType.INT32, off(f), v); return this; }
    public CStruct setLong(String f, long v)     { seg().set(CStructType.INT64, off(f), v); return this; }
    public CStruct setFloat(String f, float v)   { seg().set(CStructType.FLOAT, off(f), v); return this; }
    public CStruct setDouble(String f, double v) { seg().set(CStructType.DOUBLE, off(f), v); return this; }
    public CStruct setShort(String f, short v)   { seg().set(CStructType.INT16, off(f), v); return this; }
    public CStruct setByte(String f, byte v)     { seg().set(CStructType.INT8, off(f), v); return this; }
    public CStruct setPtr(String f, MemorySegment v) { seg().set(CStructType.PTR, off(f), v); return this; }

    public CStruct setString(String f, String v, Arena arena) {
        var str = arena.allocateFrom(v, StandardCharsets.UTF_8);
        seg().set(CStructType.PTR, off(f), str);
        return this;
    }

    // ====== 数组 ======

    public byte[] getBytes(String f) {
        return seg().asSlice(off(f)).toArray(CStructType.INT8);
    }

    public CStruct setBytes(String f, byte[] data) {
        var slice = seg().asSlice(off(f));
        for (int i = 0; i < Math.min(data.length, slice.byteSize()); i++) {
            slice.set(CStructType.INT8, i, data[i]);
        }
        return this;
    }

    // ====== 内部 ======

    public MemorySegment segment() { return segment; }

    private long off(String f) {
        var info = type.fields().get(f);
        if (info == null) throw new IllegalArgumentException("未知字段: " + f);
        return info.offset();
    }

    private MemorySegment seg() { return segment; }

    @Override public void close() {}
}
