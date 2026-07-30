package com.flora.ffi;

import java.lang.foreign.*;
import java.util.*;

/**
 * C 结构体类型模板。定义一次，可多次用于分配实例。
 *
 * <pre>{@code
 * var Person = CStructType.of(
 *     CStructType.field("id",   CStructType.INT32),
 *     CStructType.field("name", CStructType.PTR)
 * );
 *
 * try (var p = Person.create(arena)) {
 *     p.setInt("id", 1001);
 *     p.setString("name", "Alice", arena);
 * }
 * }</pre>
 */
public final class CStructType {

    private final MemoryLayout layout;
    private final Map<String, FieldInfo> fields;

    private CStructType(MemoryLayout layout, Map<String, FieldInfo> fields) {
        this.layout = layout;
        this.fields = fields;
    }

    // ====== 基本类型 ======

    public static final ValueLayout.OfByte   INT8   = ValueLayout.JAVA_BYTE;
    public static final ValueLayout.OfShort  INT16  = ValueLayout.JAVA_SHORT.withByteAlignment(1);
    public static final ValueLayout.OfInt    INT32  = ValueLayout.JAVA_INT.withByteAlignment(1);
    public static final ValueLayout.OfLong   INT64  = ValueLayout.JAVA_LONG.withByteAlignment(1);
    public static final ValueLayout.OfFloat  FLOAT  = ValueLayout.JAVA_FLOAT.withByteAlignment(1);
    public static final ValueLayout.OfDouble DOUBLE = ValueLayout.JAVA_DOUBLE.withByteAlignment(1);
    public static final AddressLayout        PTR    = ValueLayout.ADDRESS.withByteAlignment(1);

    // ====== 字段描述 ======

    public record Field(String name, MemoryLayout layout) {}

    public static Field field(String name, ValueLayout type) {
        return new Field(name, type.withName(name));
    }

    public static Field field(String name, ValueLayout type, int count) {
        return new Field(name, MemoryLayout.sequenceLayout(count, type).withName(name));
    }

    public static Field field(String name, CStructType nested) {
        return new Field(name, nested.layout.withName(name));
    }

    // ====== 构建 ======

    public static CStructType of(Field... fields) {
        if (fields.length == 0) throw new IllegalArgumentException("至少需要一个字段");
        MemoryLayout[] layouts = new MemoryLayout[fields.length];
        for (int i = 0; i < fields.length; i++) {
            layouts[i] = fields[i].layout();
        }
        MemoryLayout structLayout = MemoryLayout.structLayout(layouts);
        Map<String, FieldInfo> map = new LinkedHashMap<>();
        for (int i = 0; i < fields.length; i++) {
            long offset = structLayout.byteOffset(MemoryLayout.PathElement.groupElement(fields[i].name()));
            map.put(fields[i].name(), new FieldInfo(offset));
        }
        return new CStructType(structLayout, Collections.unmodifiableMap(map));
    }

    // ====== 实例 ======

    public CStruct create(Arena arena) {
        return new CStruct(arena.allocate(layout), this);
    }

    public CStruct wrap(MemorySegment segment) {
        return new CStruct(segment.reinterpret(layout.byteSize()), this);
    }

    // ====== 内部 ======

    record FieldInfo(long offset) {}

    Map<String, FieldInfo> fields() { return fields; }
}
