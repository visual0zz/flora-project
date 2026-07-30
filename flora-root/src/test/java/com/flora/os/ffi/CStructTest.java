package com.flora.os.ffi;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;

import static org.junit.jupiter.api.Assertions.*;

class CStructTest {

    @Test
    void intField() {
        var type = CStructType.of(CStructType.field("x", CStructType.INT32));
        try (Arena arena = Arena.ofConfined()) {
            CStruct s = type.create(arena);
            s.setInt("x", 42);
            assertEquals(42, s.getInt("x"));
        }
    }

    @Test
    void multipleFields() {
        var type = CStructType.of(
                CStructType.field("id",   CStructType.INT32),
                CStructType.field("age",  CStructType.INT32),
                CStructType.field("flag", CStructType.INT8)
        );
        try (Arena arena = Arena.ofConfined()) {
            CStruct s = type.create(arena);
            s.setInt("id", 1001).setInt("age", 30).setByte("flag", (byte) 1);
            assertEquals(1001, s.getInt("id"));
            assertEquals(30, s.getInt("age"));
            assertEquals(1, s.getByte("flag"));
        }
    }

    @Test
    void allTypes() {
        var type = CStructType.of(
                CStructType.field("b", CStructType.INT8),
                CStructType.field("s", CStructType.INT16),
                CStructType.field("i", CStructType.INT32),
                CStructType.field("l", CStructType.INT64),
                CStructType.field("f", CStructType.FLOAT),
                CStructType.field("d", CStructType.DOUBLE)
        );
        try (Arena arena = Arena.ofConfined()) {
            CStruct s = type.create(arena);
            s.setByte("b", (byte)1).setShort("s", (short)2).setInt("i", 3)
             .setLong("l", 4L).setFloat("f", 5f).setDouble("d", 6.0);
            assertEquals(1, s.getByte("b"));
            assertEquals(2, s.getShort("s"));
            assertEquals(3, s.getInt("i"));
            assertEquals(4, s.getLong("l"));
            assertEquals(5f, s.getFloat("f"), 0.001f);
            assertEquals(6.0, s.getDouble("d"), 0.001);
        }
    }

    @Test
    void pointerAndString() {
        var type = CStructType.of(
                CStructType.field("ptr", CStructType.PTR),
                CStructType.field("msg", CStructType.PTR)
        );
        try (Arena arena = Arena.ofConfined()) {
            CStruct s = type.create(arena);
            var data = arena.allocateFrom("hello");
            s.setPtr("ptr", data);
            s.setString("msg", "world", arena);
            assertEquals("hello", s.getString("ptr"));
            assertEquals("world", s.getString("msg"));
        }
    }

    @Test
    void byteArray() {
        var type = CStructType.of(CStructType.field("data", CStructType.INT8, 16));
        try (Arena arena = Arena.ofConfined()) {
            CStruct s = type.create(arena);
            s.setBytes("data", new byte[]{1, 2, 3});
            byte[] got = s.getBytes("data");
            assertEquals(1, got[0]);
            assertEquals(16, got.length);
        }
    }

    @Test
    void unknownFieldThrows() {
        var type = CStructType.of(CStructType.field("x", CStructType.INT32));
        try (Arena arena = Arena.ofConfined()) {
            CStruct s = type.create(arena);
            assertThrows(IllegalArgumentException.class, () -> s.getInt("y"));
        }
    }

    @Test
    void wrapExisting() {
        var type = CStructType.of(CStructType.field("val", CStructType.INT32));
        try (Arena arena = Arena.ofConfined()) {
            var raw = arena.allocate(4);
            raw.set(CStructType.INT32, 0, 777);
            assertEquals(777, type.wrap(raw).getInt("val"));
        }
    }
}
