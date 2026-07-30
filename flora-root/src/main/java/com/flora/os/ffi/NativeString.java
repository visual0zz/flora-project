package com.flora.os.ffi;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

/**
 * C 字符串包装，绑定 {@link Arena} 生命周期。
 */
public final class NativeString {

    private final MemorySegment segment;

    /** 编码为 UTF-8 C 字符串。 */
    public NativeString(String value, Arena arena) {
        this.segment = (value == null)
                ? MemorySegment.NULL
                : arena.allocateFrom(value, StandardCharsets.UTF_8);
    }

    private NativeString(MemorySegment segment) {
        this.segment = segment;
    }

    /** 编码为 UTF-16LE wide char 字符串（Windows 风格，含双字节 null terminator）。 */
    public static NativeString wide(String value, Arena arena) {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_16LE);
        MemorySegment seg = arena.allocate(encoded.length + 2);
        seg.copyFrom(MemorySegment.ofArray(encoded));
        seg.set(ValueLayout.JAVA_BYTE, encoded.length, (byte) 0);
        seg.set(ValueLayout.JAVA_BYTE, encoded.length + 1, (byte) 0);
        return new NativeString(seg);
    }

    public MemorySegment segment() { return segment; }
}
