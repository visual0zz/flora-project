package com.flora.runtime.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 配置加载系统的单元测试。
 */
class ConfigLoaderTest {

    // ====== ConfigFormat ======

    @Test
    void formatFromFilename() {
        assertEquals(ConfigFormat.JSON, ConfigFormat.fromFilename("config.json"));
        assertEquals(ConfigFormat.YAML, ConfigFormat.fromFilename("config.yaml"));
        assertEquals(ConfigFormat.YAML, ConfigFormat.fromFilename("config.yml"));
        assertEquals(ConfigFormat.TOML, ConfigFormat.fromFilename("config.toml"));
        assertEquals(ConfigFormat.PROPERTIES, ConfigFormat.fromFilename("config.properties"));
        assertEquals(ConfigFormat.PROPERTIES, ConfigFormat.fromFilename("config.props"));
    }

    @Test
    void formatFromFilenameUnknownThrows() {
        assertThrows(ConfigException.class, () -> ConfigFormat.fromFilename("config.xyz"));
        assertThrows(ConfigException.class, () -> ConfigFormat.fromFilename("config"));
    }

    @Test
    void formatParse() {
        ConfigMap m = ConfigMap.of(ConfigFormat.JSON.parse("{\"a\":1}"));
        assertEquals(Long.valueOf(1), m.get("a"));

        ConfigMap m2 = ConfigMap.of(ConfigFormat.YAML.parse("a: 1\n"));
        assertEquals(Long.valueOf(1), m2.get("a"));
    }

    // ====== ConfigMap ======

    @Test
    void configMapEmpty() {
        assertTrue(ConfigMap.empty().isEmpty());
    }

    @Test
    void configMapGetString() {
        ConfigMap m = ConfigMap.of(Map.of("name", "hello", "count", 42));
        assertEquals("hello", m.getString("name"));
        assertEquals("42", m.getString("count"));
        assertNull(m.getString("missing"));
        assertEquals("def", m.getString("missing", "def"));
    }

    @Test
    void configMapGetInt() {
        ConfigMap m = ConfigMap.of(Map.of("i", 42, "l", 100L));
        assertEquals(Integer.valueOf(42), m.getInt("i"));
        assertEquals(Integer.valueOf(100), m.getInt("l"));
    }

    @Test
    void configMapGetLong() {
        ConfigMap m = ConfigMap.of(Map.of("i", 42, "l", 100L));
        assertEquals(Long.valueOf(42), m.getLong("i"));
        assertEquals(Long.valueOf(100), m.getLong("l"));
    }

    @Test
    void configMapGetBoolean() {
        ConfigMap m = ConfigMap.of(Map.of("t", true, "f", false));
        assertTrue(m.getBoolean("t"));
        assertFalse(m.getBoolean("f"));
    }

    @Test
    void configMapNestedAccess() {
        ConfigMap m = ConfigMap.of(Map.of("a", Map.of("b", Map.of("c", "deep"))));
        assertEquals("deep", m.getString("a.b.c"));
        assertNotNull(m.getMap("a"));
        assertNotNull(m.getMap("a.b"));
        assertNull(m.get("a.b.c.d"));
    }

    @Test
    void configMapGetMap() {
        ConfigMap m = ConfigMap.of(Map.of("sub", Map.of("k", "v")));
        ConfigMap sub = m.getMap("sub");
        assertNotNull(sub);
        assertEquals("v", sub.getString("k"));
    }

    @Test
    void configMapGetList() {
        ConfigMap m = ConfigMap.of(Map.of("items", List.of(1, 2, 3)));
        List<Object> list = m.getList("items");
        assertNotNull(list);
        assertEquals(3, list.size());
    }

    @Test
    void configMapContains() {
        ConfigMap m = ConfigMap.of(Map.of("a", 1));
        assertTrue(m.contains("a"));
        assertFalse(m.contains("b"));
    }

    @Test
    void configMapDeepCopy() {
        Map<String, Object> raw = new java.util.LinkedHashMap<>();
        raw.put("key", "value");
        ConfigMap m = ConfigMap.of(raw);
        raw.put("key", "modified");
        assertEquals("value", m.getString("key"));
    }

    // ====== StringConfigSource ======

    @Test
    void stringConfigSource() {
        ConfigSource src = new StringConfigSource(ConfigFormat.JSON, "{\"a\":1}");
        ConfigMap m = src.load();
        assertEquals(Long.valueOf(1), m.get("a"));
    }

    // ====== ClasspathConfigSource ======

    @Test
    void classpathConfigSource() {
        ConfigSource src = new ClasspathConfigSource("config/app.yaml");
        ConfigMap m = src.load();
        assertEquals("test-app", m.getString("app.name"));
        assertEquals(Long.valueOf(8080), m.get("server.port"));
    }

    @Test
    void classpathConfigSourceNotFoundThrows() {
        ConfigSource src = new ClasspathConfigSource("nonexistent/file.yaml");
        assertThrows(ConfigException.class, src::load);
    }

    // ====== FileConfigSource ======

    @Test
    void fileConfigSourceNotFoundThrows() {
        ConfigSource src = new FileConfigSource(Paths.get("/nonexistent/file.yaml"));
        assertThrows(ConfigException.class, src::load);
    }

    // ====== ConfigLoader ======

    @Test
    void configLoaderEmptyReturnsEmpty() {
        assertTrue(new ConfigLoader().load().isEmpty());
    }

    @Test
    void configLoaderSingleSource() {
        ConfigLoader loader = new ConfigLoader();
        loader.addSource(new StringConfigSource(ConfigFormat.JSON, "{\"a\":1, \"b\":\"x\"}"));
        ConfigMap m = loader.load();
        assertEquals(Long.valueOf(1), m.get("a"));
        assertEquals("x", m.getString("b"));
    }

    @Test
    void configLoaderMergeOverride() {
        ConfigLoader loader = new ConfigLoader();
        loader.addSource(new StringConfigSource(ConfigFormat.JSON, "{\"a\":1, \"b\":2}"));
        loader.addSource(new StringConfigSource(ConfigFormat.JSON, "{\"b\":3, \"c\":4}"));
        ConfigMap m = loader.load();
        assertEquals(Long.valueOf(1), m.get("a"));
        assertEquals(Long.valueOf(3), m.get("b"));  // 后添加的覆盖
        assertEquals(Long.valueOf(4), m.get("c"));
    }

    @Test
    void configLoaderMergeDeep() {
        ConfigLoader loader = new ConfigLoader();
        loader.addSource(new StringConfigSource(ConfigFormat.JSON,
                "{\"db\":{\"host\":\"localhost\",\"port\":3306}}"));
        loader.addSource(new StringConfigSource(ConfigFormat.JSON,
                "{\"db\":{\"port\":5432,\"user\":\"admin\"}}"));
        ConfigMap m = loader.load();
        assertEquals("localhost", m.getString("db.host"));
        assertEquals(Long.valueOf(5432), m.get("db.port"));  // 覆盖
        assertEquals("admin", m.getString("db.user"));
    }

    @Test
    void configLoaderMultipleFormats() {
        ConfigLoader loader = new ConfigLoader();
        loader.addSource(new StringConfigSource(ConfigFormat.JSON, "{\"from\":\"json\"}"));
        loader.addSource(new StringConfigSource(ConfigFormat.YAML, "from: yaml\ncount: 3\n"));
        loader.addSource(new StringConfigSource(ConfigFormat.TOML, "from = \"toml\"\n"));
        ConfigMap m = loader.load();
        assertEquals("toml", m.getString("from"));  // 最后添加的覆盖
        assertEquals(Long.valueOf(3), m.get("count"));
    }

    // ====== 全局单例 ======

    @Test
    void systemSingleton() {
        // system() 返回同一个实例
        assertSame(ConfigLoader.system(), ConfigLoader.system());
    }

    @Test
    void systemInstanceIsolation() {
        // 独立实例不与 system() 共享来源
        ConfigLoader loader = new ConfigLoader();
        loader.addSource(new StringConfigSource(ConfigFormat.JSON, "{\"k\":\"v\"}"));
        assertTrue(ConfigLoader.system().getSources().isEmpty());
        assertEquals(1, loader.getSources().size());
    }

    // ====== 包含指令 ======

    @Test
    void classpathIncludeResolvesCorrectly() {
        // 使用 classpath: 前缀在配置中包含类路径资源
        String yaml = "flora:\n  config:\n    includes:\n      - classpath:config/database.yaml\napp:\n  name: main\n";
        ConfigLoader loader = new ConfigLoader();
        loader.addSource(new StringConfigSource(ConfigFormat.YAML, yaml));
        ConfigMap m = loader.load();
        assertEquals("main", m.getString("app.name"));
        assertEquals(Long.valueOf(9090), m.get("server.port"));
        assertEquals("jdbc:postgresql://localhost/mydb", m.getString("database.url"));
    }
}
