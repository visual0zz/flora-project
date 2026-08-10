package com.flora.codec.json.model;

import com.flora.codec.json.JsonParser;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JsonObject 类族单元测试：类型安全取值、与 Map 原生树双向转换、与 Java Bean 双向转换。
 */
class JsonObjectTest {

    @Test
    void typeSafeAccessors() {
        JsonObject o = JsonParser.parseObject("{\"s\":\"x\",\"i\":42,\"f\":3.14,\"b\":true,\"n\":null,"
                + "\"sub\":{\"k\":1},\"arr\":[1,2]}");
        assertEquals("x", o.getString("s"));
        assertEquals(Long.valueOf(42), o.getLong("i"));
        assertEquals(Integer.valueOf(42), o.getInt("i"));
        assertEquals(3.14, o.getDouble("f"), 1e-9);
        assertEquals(Boolean.TRUE, o.getBool("b"));
        assertNull(o.getString("n"));
        assertEquals(1L, o.getObject("sub").getLong("k"));
        assertEquals(2, o.getArray("arr").size());
        assertInstanceOf(JsonNumber.class, o.getNumber("i"));
    }

    @Test
    void accessorOnWrongTypeThrows() {
        JsonObject o = JsonParser.parseObject("{\"s\":\"x\"}");
        assertThrows(IllegalStateException.class, () -> o.getLong("s"));
        assertThrows(IllegalStateException.class, () -> o.getObject("s"));
    }

    @Test
    void putWithNativeValueWraps() {
        JsonObject o = new JsonObject();
        o.put("a", 1).put("b", "x").put("c", true).put("d", (Object) null);
        assertTrue(o.get("a").isNumber());
        assertTrue(o.get("b").isString());
        assertTrue(o.get("c").isBool());
        assertTrue(o.get("d").isNull());
    }

    @Test
    void toMapRoundTrip() {
        JsonObject o = JsonParser.parseObject("{\"a\":1,\"b\":[1,2],\"c\":{\"d\":true}}");
        Map<String, Object> map = o.toMap();
        assertEquals(1L, map.get("a"));
        assertEquals(List.of(1L, 2L), map.get("b"));
        @SuppressWarnings("unchecked")
        Map<String, Object> c = (Map<String, Object>) map.get("c");
        assertEquals(Boolean.TRUE, c.get("d"));
        // 修改原生树不影响原 JsonObject
        map.put("a", 99);
        assertEquals(1L, o.getLong("a"));
    }

    @Test
    void fromMapWrapsNativeTree() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("a", 1);
        map.put("list", List.of(1, 2));
        JsonObject o = JsonObject.fromMap(map);
        assertEquals(1L, o.getLong("a"));
        assertEquals(2, o.getArray("list").size());
    }

    // ====== Bean 互转 ======

    static class Sample {
        private String name;
        private int age;
        private boolean active;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
    }

    @Test
    void fromBeanCollectsGetters() {
        Sample s = new Sample();
        s.setName("alice");
        s.setAge(30);
        s.setActive(true);
        JsonObject o = JsonObject.fromBean(s);
        assertEquals("alice", o.getString("name"));
        assertEquals(30L, o.getLong("age"));
        assertEquals(Boolean.TRUE, o.getBool("active"));
    }

    @Test
    void toBeanPopulatesViaSetter() {
        JsonObject o = JsonParser.parseObject("{\"name\":\"bob\",\"age\":25,\"active\":false}");
        Sample s = o.toBean(Sample.class);
        assertEquals("bob", s.getName());
        assertEquals(25, s.getAge());
        assertFalse(s.isActive());
    }

    @Test
    void beanRoundTrip() {
        Sample s = new Sample();
        s.setName("carol");
        s.setAge(40);
        s.setActive(true);
        Sample back = JsonObject.fromBean(s).toBean(Sample.class);
        assertEquals(s.getName(), back.getName());
        assertEquals(s.getAge(), back.getAge());
        assertEquals(s.isActive(), back.isActive());
    }

    static class Ignored {
        @JsonIgnore
        private String secret = "x";
        private String publicField = "y";
        public String getSecret() { return secret; }
        public String getPublicField() { return publicField; }
    }

    @Test
    void beanJsonIgnoreExcluded() {
        JsonObject o = JsonObject.fromBean(new Ignored());
        assertFalse(o.containsKey("secret"));
        assertTrue(o.containsKey("publicField"));
    }

    @Test
    void equalityAndCopy() {
        JsonObject a = JsonParser.parseObject("{\"x\":1}");
        JsonObject b = JsonParser.parseObject("{\"x\":1}");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        JsonObject c = a.copy();
        assertEquals(a, c);
        c.put("x", 2);
        assertEquals(1L, a.getLong("x"));
    }
}
