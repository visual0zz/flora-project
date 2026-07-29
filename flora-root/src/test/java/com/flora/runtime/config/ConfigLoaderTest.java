package com.flora.runtime.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.Collections;
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
        assertSame(ConfigLoader.system(), ConfigLoader.system());
    }

    @Test
    void systemInstanceIsolation() {
        ConfigLoader loader = new ConfigLoader();
        loader.addSource(new StringConfigSource(ConfigFormat.JSON, "{\"k\":\"v\"}"));
        assertTrue(ConfigLoader.system().getSources().isEmpty());
        assertEquals(1, loader.getSources().size());
    }

    // ====== resolve：Java 编排分阶段加载 ======

    @Test
    void resolveWithNoExtraSources() {
        // 回调返回空 —— resolve 退化为普通 load
        ConfigLoader loader = new ConfigLoader();
        loader.addSource(new StringConfigSource(ConfigFormat.JSON, "{\"a\":1}"));
        ConfigMap m = loader.resolve(cfg -> Collections.emptyList());
        assertEquals(Long.valueOf(1), m.get("a"));
    }

    @Test
    void resolveReadsRegularKeyToLoadMore() {
        // 模拟：第一个配置中有一个普通 key "database.config" 指向第二个配置文件
        String mainYaml = "app:\n  name: main\nserver:\n  port: 8080\n";

        ConfigLoader loader = new ConfigLoader();
        loader.addSource(new StringConfigSource(ConfigFormat.YAML, mainYaml, "main.yaml"));

        ConfigMap m = loader.resolve(cfg -> {
            // 读取一个普通 app key "app.name" —— 无任何特殊含义
            String name = cfg.getString("app.name");
            if ("main".equals(name)) {
                return List.of(new ClasspathConfigSource("config/database.yaml"));
            }
            return Collections.emptyList();
        });

        assertEquals("main", m.getString("app.name"));
        // database.yaml 后加载，其中的 server.port:9090 覆盖了 main 中的 8080
        assertEquals(Long.valueOf(9090), m.get("server.port"));
        assertEquals("jdbc:postgresql://localhost/mydb", m.getString("database.url"));
    }

    @Test
    void resolveChainMultipleRounds() {
        // 三轮加载：每个阶段检查不同的普通 key
        String first = "stage: 1\nimport:\n  next: true\n";
        String second = "stage: 2\ncount: 42\n";
        String third = "stage: 3\ndone: true\n";

        ConfigLoader loader = new ConfigLoader();
        loader.addSource(new StringConfigSource(ConfigFormat.YAML, first, "first.yaml"));

        ConfigMap m = loader.resolve(cfg -> {
            String done = cfg.getString("done");
            if ("true".equals(done)) return Collections.emptyList();

            String importNext = cfg.getString("import.next");
            if ("true".equals(importNext)) {
                switch (cfg.getString("stage", "")) {
                    case "1": return List.of(new StringConfigSource(ConfigFormat.YAML, second, "second.yaml"));
                    case "2": return List.of(new StringConfigSource(ConfigFormat.YAML, third, "third.yaml"));
                }
            }
            return Collections.emptyList();
        });

        assertEquals("3", m.getString("stage"));
        assertEquals(Long.valueOf(42), m.get("count"));
        assertTrue(m.getBoolean("done"));
    }

    @Test
    void resolveCycleDetection() {
        // 同一位置重复解析 -> 自动跳过
        ConfigLoader loader = new ConfigLoader();
        loader.addSource(new StringConfigSource(ConfigFormat.JSON, "{\"x\":1}", "cyclic.yaml"));

        ConfigMap m = loader.resolve(cfg -> {
            // 每次回调都返回同一个来源 —— loadedLocations 会阻止重复加载
            return List.of(new StringConfigSource(ConfigFormat.JSON, "{\"x\":2}", "cyclic.yaml"));
        });

        // 第二次加载被跳过，值应为 1（非 2）
        assertEquals(Long.valueOf(1), m.get("x"));
    }
}
