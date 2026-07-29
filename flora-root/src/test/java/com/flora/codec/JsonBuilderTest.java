package com.flora.codec;

import com.flora.codec.json.JsonBuilder;
import com.flora.codec.json.JsonIgnore;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JsonBuilder 序列化器的独立单元测试。
 */
class JsonBuilderTest {

    @Test
    void serializeNull() {
        assertEquals("null", JsonBuilder.toJsonString(null));
    }

    @Test
    void serializeString() {
        assertEquals("\"hello\"", JsonBuilder.toJsonString("hello"));
    }

    @Test
    void serializeStringWithEscapes() {
        assertEquals("\"a\\nb\\tc\\\"d\\\\\"", JsonBuilder.toJsonString("a\nb\tc\"d\\"));
    }

    @Test
    void serializeNumber() {
        assertEquals("42", JsonBuilder.toJsonString(42));
        assertEquals("3.14", JsonBuilder.toJsonString(3.14));
        assertEquals("-1", JsonBuilder.toJsonString(-1));
    }

    @Test
    void serializeBoolean() {
        assertEquals("true", JsonBuilder.toJsonString(true));
        assertEquals("false", JsonBuilder.toJsonString(false));
    }

    @Test
    void serializeList() {
        assertEquals("[1,2,3]", JsonBuilder.toJsonString(List.of(1, 2, 3)));
    }

    @Test
    void serializeEmptyList() {
        assertEquals("[]", JsonBuilder.toJsonString(List.of()));
    }

    @Test
    void serializeMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("a", 1);
        m.put("b", "x");
        assertEquals("{\"a\":1,\"b\":\"x\"}", JsonBuilder.toJsonString(m));
    }

    @Test
    void serializeEmptyMap() {
        assertEquals("{}", JsonBuilder.toJsonString(Map.of()));
    }

    @Test
    void serializeNested() {
        Map<String, Object> inner = Map.of("y", 2);
        Map<String, Object> outer = Map.of("x", inner);
        assertEquals("{\"x\":{\"y\":2}}", JsonBuilder.toJsonString(outer));
    }

    @Test
    void serializeEnum() {
        assertEquals("\"SECONDS\"", JsonBuilder.toJsonString(java.util.concurrent.TimeUnit.SECONDS));
    }

    @Test
    void serializeCharacter() {
        assertEquals("\"A\"", JsonBuilder.toJsonString('A'));
    }

    @Test
    void serializeCharArray() {
        assertEquals("[\"A\",\"B\"]", JsonBuilder.toJsonString(new char[]{'A', 'B'}));
    }

    @Test
    void serializeIntArray() {
        assertEquals("[1,2,3]", JsonBuilder.toJsonString(new int[]{1, 2, 3}));
    }

    @Test
    void serializeDoubleArray() {
        assertEquals("[1,2]", JsonBuilder.toJsonString(new double[]{1.0, 2.0}));
    }

    @Test
    void serializeBeanWithGetters() {
        String json = JsonBuilder.toJsonString(new Bean("test", 42));
        assertTrue(json.contains("\"name\":\"test\""), json);
        assertTrue(json.contains("\"value\":42"), json);
    }

    @Test
    void serializeBeanWithBooleanGetter() {
        String json = JsonBuilder.toJsonString(new BoolBean(true, false));
        assertTrue(json.contains("\"active\":true"), json);
        assertTrue(json.contains("\"enabled\":false"), json);
    }

    @Test
    void prettyJsonString() {
        Map<String, Object> m = Map.of("a", 1);
        String pretty = JsonBuilder.toPrettyJsonString(m);
        assertTrue(pretty.contains("\n"));
        assertTrue(pretty.contains("  "));
    }

    @Test
    void nanThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> JsonBuilder.toJsonString(Double.NaN));
    }

    @Test
    void infinityThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> JsonBuilder.toJsonString(Double.POSITIVE_INFINITY));
    }

    @Test
    void nullKeyInMap() {
        Map<Object, Object> m = new LinkedHashMap<>();
        m.put(null, 1);
        m.put("a", 2);
        assertEquals("{\"null\":1,\"a\":2}", JsonBuilder.toJsonString(m));
    }

    @Test
    void cycleDetection() {
        Map<Object, Object> m = new LinkedHashMap<>();
        m.put("self", m);
        assertThrows(IllegalArgumentException.class,
                () -> JsonBuilder.toJsonString(m));
    }

    // -- 内部 Bean --

    static class Bean {
        private final String name;
        private final int value;
        Bean(String name, int value) { this.name = name; this.value = value; }
        public String getName() { return name; }
        public int getValue() { return value; }
    }

    static class BoolBean {
        private final boolean active;
        private final boolean enabled;
        BoolBean(boolean active, boolean enabled) { this.active = active; this.enabled = enabled; }
        public boolean isActive() { return active; }
        public boolean isEnabled() { return enabled; }
    }
}
