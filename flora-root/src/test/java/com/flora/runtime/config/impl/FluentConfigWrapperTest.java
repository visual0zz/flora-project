package com.flora.runtime.config.impl;

import com.flora.runtime.config.ConfigException;
import com.flora.runtime.config.interfaces.Config;
import com.flora.runtime.config.interfaces.FluentConfig;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link FluentConfigWrapper} 的单元测试。
 */
class FluentConfigWrapperTest {

    /** 内存 Config 桩：透传类型，不做任何转换。 */
    private static final class MemConfig implements Config {
        private final Map<String, Object> data;

        MemConfig(Map<String, Object> data) {
            this.data = data;
        }

        @Override
        public Object get(String path) {
            Object current = data;
            for (String key : path.split("\\.")) {
                if (!(current instanceof Map m)) return null;
                current = m.get(key);
                if (current == null) return null;
            }
            return current;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Config getSubConfig(String path) {
            Object v = get(path);
            if (v == null) return null;
            if (v instanceof Map m) return new MemConfig((Map<String, Object>) m);
            throw new ConfigException("不是映射: " + v.getClass().getName());
        }

        @Override
        public Map<String, Object> toMapTree() {
            return data;
        }

        @Override
        public Map<String, Object> toLongKeyMap() {
            Map<String, Object> flat = new LinkedHashMap<>();
            flatten("", data, flat);
            return flat;
        }

        @Override
        public boolean isEmpty() {
            return data.isEmpty();
        }

        @SuppressWarnings("unchecked")
        private static void flatten(String prefix, Map<String, Object> map, Map<String, Object> out) {
            for (Map.Entry<String, Object> e : map.entrySet()) {
                String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
                Object v = e.getValue();
                if (v instanceof Map) {
                    flatten(key, (Map<String, Object>) v, out);
                } else {
                    out.put(key, v);
                }
            }
        }
    }

    private static Map<String, Object> sampleData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", "hello");
        data.put("count", 42L);
        data.put("port", "3306");
        data.put("debug", Boolean.TRUE);
        data.put("flag", "FALSE");
        data.put("items", List.of(1, 2, 3));
        Map<String, Object> db = new LinkedHashMap<>();
        db.put("host", "localhost");
        db.put("port", 5432L);
        data.put("db", db);
        return data;
    }

    // ====== of 工厂 ======

    @Test
    void ofWrapsPlainConfig() {
        FluentConfig fluent = FluentConfig.of(new MemConfig(sampleData()));
        assertNotNull(fluent);
        assertEquals("hello", fluent.getString("name"));
    }

    @Test
    void ofNullThrows() {
        assertThrows(ConfigException.class, () -> FluentConfig.of(null));
    }

    @Test
    void ofReturnsSameForAlreadyFluent() {
        FluentConfig fluent = FluentConfig.of(new MemConfig(sampleData()));
        assertSame(fluent, FluentConfig.of(fluent));
    }

    // ====== 转发 ======

    @Test
    void getForwardsRawType() {
        FluentConfig fluent = FluentConfig.of(new MemConfig(sampleData()));
        assertEquals(42L, fluent.get("count"));       // Long 原样透传
        assertEquals("3306", fluent.get("port"));     // String 原样透传
    }

    @Test
    void getSubConfigReturnsFluentForChain() {
        FluentConfig fluent = FluentConfig.of(new MemConfig(sampleData()));
        FluentConfig db = fluent.getSubConfig("db");
        assertNotNull(db);
        assertEquals("localhost", db.getString("host"));
        assertEquals(Integer.valueOf(5432), db.getInt("port"));
    }

    @Test
    void getSubConfigMissingReturnsNull() {
        FluentConfig fluent = FluentConfig.of(new MemConfig(sampleData()));
        assertNull(fluent.getSubConfig("nope"));
    }

    @Test
    void forwardsMapTreeLongKeyAndEmpty() {
        FluentConfig fluent = FluentConfig.of(new MemConfig(sampleData()));
        assertFalse(fluent.isEmpty());
        assertEquals("hello", fluent.toMapTree().get("name"));
        Map<String, Object> flat = fluent.toLongKeyMap();
        assertEquals("hello", flat.get("name"));
        assertEquals("localhost", flat.get("db.host"));
        assertEquals(5432L, flat.get("db.port"));
        assertEquals(8, flat.size());
        assertTrue(FluentConfig.of(new MemConfig(Map.of())).isEmpty());
    }

    // ====== 类型转换 ======

    @Test
    void getStringConversions() {
        FluentConfig fluent = FluentConfig.of(new MemConfig(sampleData()));
        assertEquals("hello", fluent.getString("name"));
        assertEquals("42", fluent.getString("count"));       // Long -> toString
        assertEquals("3306", fluent.getString("port"));      // String 原样
        assertNull(fluent.getString("missing"));
    }

    @Test
    void getIntConversions() {
        FluentConfig fluent = FluentConfig.of(new MemConfig(sampleData()));
        assertEquals(Integer.valueOf(42), fluent.getInt("count"));    // Long -> int
        assertEquals(Integer.valueOf(3306), fluent.getInt("port"));   // String -> int
        assertEquals(Integer.valueOf(5432), fluent.getSubConfig("db").getInt("port"));
        assertNull(fluent.getInt("missing"));
    }

    @Test
    void getIntUnconvertibleThrows() {
        FluentConfig fluent = FluentConfig.of(new MemConfig(sampleData()));
        assertThrows(ConfigException.class, () -> fluent.getInt("name"));
        assertThrows(ConfigException.class, () -> fluent.getInt("debug"));
    }

    @Test
    void getLongConversions() {
        FluentConfig fluent = FluentConfig.of(new MemConfig(sampleData()));
        assertEquals(Long.valueOf(42), fluent.getLong("count"));      // Long 原样
        assertEquals(Long.valueOf(3306), fluent.getLong("port"));     // String -> long
        assertNull(fluent.getLong("missing"));
        assertThrows(ConfigException.class, () -> fluent.getLong("name"));
    }

    @Test
    void getBooleanConversions() {
        FluentConfig fluent = FluentConfig.of(new MemConfig(sampleData()));
        assertEquals(Boolean.TRUE, fluent.getBoolean("debug"));       // Boolean 原样
        assertEquals(Boolean.FALSE, fluent.getBoolean("flag"));       // "FALSE" 字符串
        assertNull(fluent.getBoolean("missing"));
        assertThrows(ConfigException.class, () -> fluent.getBoolean("name"));
    }

    @Test
    void getListConversions() {
        FluentConfig fluent = FluentConfig.of(new MemConfig(sampleData()));
        assertEquals(List.of(1, 2, 3), fluent.getList("items"));
        assertNull(fluent.getList("missing"));
        assertThrows(ConfigException.class, () -> fluent.getList("name"));
        assertThrows(UnsupportedOperationException.class,
                () -> fluent.getList("items").add(4));   // 返回不可变列表
    }

    // ====== default 方法 ======

    @Test
    void orDefaultVariants() {
        FluentConfig fluent = FluentConfig.of(new MemConfig(sampleData()));
        assertEquals(3306, fluent.getIntOrDefault("port", 0));
        assertEquals(7, fluent.getIntOrDefault("missing", 7));
        assertEquals("def", fluent.getStringOrDefault("missing", "def"));
        assertEquals(42L, fluent.getLongOrDefault("count", 0L));
        assertEquals(true, fluent.getBooleanOrDefault("debug", false));
        assertEquals(false, fluent.getBooleanOrDefault("missing", false));
    }
}
