package com.flora.codec;

import com.flora.codec.json.JsonBuilder;
import com.flora.codec.json.JsonParser;
import com.flora.codec.json.model.JsonIgnore;
import com.flora.codec.json.model.JsonNumber;
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

    // ── P0: JsonBuilder getter 序列化 ──

    static class SampleBean {
        private final String name;
        private final int value;
        SampleBean(String name, int value) {
            this.name = name; this.value = value;
        }
        public String getName() { return name; }
        public int getValue() { return value; }
    }

    @Test
    void beanWithGettersSerializes() {
        String json = JsonBuilder.toJsonString(new SampleBean("x", 7));
        assertTrue(json.contains("\"name\":\"x\""), json);
        assertTrue(json.contains("\"value\":7"), json);
    }

    // ── Getter 序列化：继承 ──

    static class GetterBean {
        private String base = "base";
        public String getBase() { return base; }
    }

    static class InheritedBean extends GetterBean {
        private int value = 42;
        public int getValue() { return value; }
    }

    @Test
    void beanWithInheritedGetters() {
        String json = JsonBuilder.toJsonString(new InheritedBean());
        assertTrue(json.contains("\"base\":\"base\""), json);
        assertTrue(json.contains("\"value\":42"), json);
    }

    // ── Getter 序列化：boolean isXxx() ──

    static class BooleanBean {
        private boolean active = true;
        private Boolean enabled = false;
        public boolean isActive() { return active; }
        public Boolean isEnabled() { return enabled; }
    }

    @Test
    void beanWithBooleanIsGetter() {
        String json = JsonBuilder.toJsonString(new BooleanBean());
        assertTrue(json.contains("\"active\":true"), json);
        assertTrue(json.contains("\"enabled\":false"), json);
    }

    // ── Getter 序列化：方法上的 @JsonIgnore ──

    static class IgnoreMethodBean {
        private String name = "x";
        private String secret = "hidden";
        public String getName() { return name; }
        @JsonIgnore public String getSecret() { return secret; }
    }

    @Test
    void beanWithMethodJsonIgnore() {
        String json = JsonBuilder.toJsonString(new IgnoreMethodBean());
        assertTrue(json.contains("\"name\":\"x\""), json);
        assertFalse(json.contains("secret"), json);
    }

    // ── Getter 序列化：字段上的 @JsonIgnore ──

    static class IgnoreFieldBean {
        @JsonIgnore private String ignoreMe = "ignored";
        private String visible = "ok";
        public String getIgnoreMe() { return ignoreMe; }
        public String getVisible() { return visible; }
    }

    @Test
    void beanWithFieldJsonIgnore() {
        String json = JsonBuilder.toJsonString(new IgnoreFieldBean());
        assertTrue(json.contains("\"visible\":\"ok\""), json);
        assertFalse(json.contains("ignoreMe"), json);
    }

    // ── Getter 序列化：getClass() 不出现 ──

    @Test
    void getClassIsSkipped() {
        String json = JsonBuilder.toJsonString(new SampleBean("a", 1));
        assertFalse(json.contains("class"), json);
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
        JsonNumber v = JsonParser.parse("1e+10").asNumber();
        assertEquals(0, v.decimalValue().compareTo(BigDecimal.TEN.pow(10)));
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

    // ====== HexUtil 补充测试 ======

    @Test
    void encodeHexStringOverload() {
        assertEquals(HexUtil.encodeHex("hello"), HexUtil.encodeHex("hello".getBytes()));
    }

    @Test
    void decodeHexToString() {
        assertEquals("hello", HexUtil.decodeHexToString("68656c6c6f"));
    }

    @Test
    void encodeHexEmptyArray() {
        assertEquals("", HexUtil.encodeHex(new byte[0]));
    }

    @Test
    void isValidHex() {
        assertTrue(HexUtil.isValidHex("68656c6c6f"));
        assertTrue(HexUtil.isValidHex("ABCDEF"));
        assertTrue(HexUtil.isValidHex("abcdef"));
        assertFalse(HexUtil.isValidHex(null));
        assertFalse(HexUtil.isValidHex(""));
        assertFalse(HexUtil.isValidHex("xyz"));
        assertFalse(HexUtil.isValidHex("abc")); // 奇数长度
        assertFalse(HexUtil.isValidHex("ABFG")); // G 非法
    }

    @Test
    void decodeHexMixedCase() {
        byte[] expected = {(byte) 0xAB, (byte) 0x00, (byte) 0xFF};
        assertArrayEquals(expected, HexUtil.decodeHex("ab00ff"));
        assertArrayEquals(expected, HexUtil.decodeHex("AB00FF"));
        assertArrayEquals(expected, HexUtil.decodeHex("Ab00Ff"));
    }

    @Test
    void decodeHexNullThrows() {
        assertThrows(NullPointerException.class, () -> HexUtil.decodeHex(null));
        assertThrows(NullPointerException.class, () -> HexUtil.decodeHexToString(null));
    }

    @Test
    void encodeHexNullThrows() {
        assertThrows(NullPointerException.class, () -> HexUtil.encodeHex((byte[]) null));
        assertThrows(NullPointerException.class, () -> HexUtil.encodeHex((String) null));
    }
}
