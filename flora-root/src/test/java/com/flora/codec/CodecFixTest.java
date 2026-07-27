package com.flora.codec;

import com.flora.codec.json.JsonBuilder;
import com.flora.codec.json.JsonParser;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CodecFixTest {

    // ── P0: HexUtil.decodeHex 校验 (AIOOBE + 静默损坏) ──

    @Test
    void decodeHexValidRoundTrip() {
        byte[] data = {(byte) 0xAB, (byte) 0x00, (byte) 0xFF};
        String hex = HexUtil.encodeHex(data);
        assertArrayEquals(data, HexUtil.decodeHex(hex));
        assertArrayEquals(data, HexUtil.decodeHex(hex.toUpperCase()));
    }

    @Test
    void decodeHexEmptyReturnsEmptyArray() {
        assertArrayEquals(new byte[0], HexUtil.decodeHex(""));
    }

    @Test
    void decodeHexInvalidAsciiThrows() {
        assertThrows(IllegalArgumentException.class, () -> HexUtil.decodeHex("ZZ"));
    }

    @Test
    void decodeHexNonAsciiThrows() {
        // 字符 > 127 曾导致 ArrayIndexOutOfBoundsException
        assertThrows(IllegalArgumentException.class, () -> HexUtil.decodeHex("\u00FF\u00FF"));
    }

    @Test
    void decodeHexOddLengthThrows() {
        assertThrows(IllegalArgumentException.class, () -> HexUtil.decodeHex("abc"));
    }

    // ── P0: JsonBuilder 反射 setAccessible 不应崩溃 (JPMS) ──

    static class SampleBean {
        private final String name;
        private final int value;
        SampleBean(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }

    @Test
    void beanWithPrivateFieldsSerializes() {
        // 验证私有字段反射路径未被 catch 改动破坏
        String json = JsonBuilder.toJsonString(new SampleBean("x", 7));
        assertTrue(json.contains("\"name\":\"x\""), json);
        assertTrue(json.contains("\"value\":7"), json);
    }

    // ── P1: JsonParser 递归深度限制 ──

    @Test
    void deeplyNestedJsonThrows() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1500; i++) sb.append('[');
        sb.append('0');
        for (int i = 0; i < 1500; i++) sb.append(']');
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> JsonParser.parse(sb.toString()));
        assertTrue(ex.getMessage().contains("嵌套层级过深"), ex.getMessage());
    }

    // ── P1: JsonBuilder 含 null 键的 Map ──

    @Test
    void mapWithNullKeyDoesNotThrow() {
        Map<Object, Object> m = new LinkedHashMap<>();
        m.put(null, 1);
        m.put("a", 2);
        String json = JsonBuilder.toJsonString(m);
        assertEquals("{\"null\":1,\"a\":2}", json);
    }

    // ── P2: JsonParser 支持 1e+10 ──

    @Test
    void parseExponentWithPlusSign() {
        Object v = JsonParser.parse("1e+10");
        assertTrue(v instanceof BigDecimal);
        assertEquals(0, ((BigDecimal) v).compareTo(BigDecimal.TEN.pow(10)));
    }

    // ── P2: JsonBuilder 积分 Double 超出 long 范围 ──

    @Test
    void integralDoubleBeyondLongRange() {
        assertEquals("1.0E20", JsonBuilder.toJsonString(Double.valueOf(1e20)));
        assertEquals("5", JsonBuilder.toJsonString(5.0));
    }

    // ── P2: JsonBuilder Character / char[] ──

    @Test
    void characterSerializedAsString() {
        assertEquals("\"A\"", JsonBuilder.toJsonString('A'));
    }

    @Test
    void charArraySerializedAsStringArray() {
        assertEquals("[\"A\",\"B\"]", JsonBuilder.toJsonString(new char[]{'A', 'B'}));
    }
}
