package com.flora.runtime.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ConfigUtil} 流式配置 API 的单元测试。
 */
class ConfigUtilTest {

    @AfterEach
    void cleanSystem() {
        ConfigLoader.system().clearSources();
    }

    @Test
    void newConfigWithFile() {
        ConfigMap m = ConfigUtil.newConfig()
                .loadFile("src/test/resources/config/app.yaml");
        assertEquals("test-app", m.getString("app.name"));
        assertEquals(Long.valueOf(8080), m.get("server.port"));
    }

    @Test
    void newConfigWithString() {
        ConfigMap m = ConfigUtil.newConfig()
                .loadString("name=hello\ncount=42");
        assertEquals("hello", m.getString("name"));
        assertEquals("42", m.getString("count"));
    }

    @Test
    void chainedMerge() {
        ConfigMap m = ConfigUtil.newConfig()
                .loadString("key=first\nshared=from_first")
                .loadString("key=second\nshared=from_second");
        assertEquals("second", m.getString("key"));
        assertEquals("from_second", m.getString("shared"));
    }

    @Test
    void chainWithFileAndString() {
        ConfigMap m = ConfigUtil.newConfig()
                .loadString("app.name=override")
                .loadFile("src/test/resources/config/app.yaml");
        assertEquals("test-app", m.getString("app.name"));
    }

    @Test
    void placeholderResolvedFromSystemProperty() {
        String key = "test.config.path." + System.nanoTime();
        System.setProperty(key, "src/test/resources/config/app.yaml");
        try {
            ConfigMap m = ConfigUtil.newConfig().loadFile("{" + key + "}");
            assertEquals("test-app", m.getString("app.name"));
        } finally {
            System.clearProperty(key);
        }
    }

    @Test
    void placeholderInMiddleOfPath() {
        String key = "test.config.dir." + System.nanoTime();
        System.setProperty(key, "src/test/resources/config");
        try {
            ConfigMap m = ConfigUtil.newConfig().loadFile("{" + key + "}/app.yaml");
            assertEquals("test-app", m.getString("app.name"));
        } finally {
            System.clearProperty(key);
        }
    }

    @Test
    void placeholderNotFoundThrows() {
        assertThrows(ConfigException.class, () ->
                ConfigUtil.newConfig().loadFile("{nonexistent.key}"));
    }

    @Test
    void classpathPrefix() {
        ConfigMap m = ConfigUtil.newConfig().loadFile("classpath:config/app.yaml");
        assertEquals("test-app", m.getString("app.name"));
    }

    @Test
    void multiplePlaceholders() {
        String key1 = "test.p1." + System.nanoTime();
        String key2 = "test.p2." + System.nanoTime();
        System.setProperty(key1, "src/test");
        System.setProperty(key2, "config/app.yaml");
        try {
            ConfigMap m = ConfigUtil.newConfig()
                    .loadFile("{" + key1 + "}/resources/{" + key2 + "}");
            assertEquals("test-app", m.getString("app.name"));
        } finally {
            System.clearProperty(key1);
            System.clearProperty(key2);
        }
    }

    // ====== 独立实例 ======

    @Test
    void newConfigIsIndependent() {
        ConfigMap m1 = ConfigUtil.newConfig().loadString("k=from_first");
        ConfigMap m2 = ConfigUtil.newConfig().loadString("k=from_second");
        assertEquals("from_first", m1.getString("k"));
        assertEquals("from_second", m2.getString("k"));
    }

    // ====== 全局单例 ======

    @Test
    void systemSharedAcrossCalls() {
        ConfigUtil.system().loadString("shared_key=initial");
        ConfigUtil.system().loadString("another=val");
        ConfigMap m = ConfigUtil.system().loadString("last=third");
        assertEquals("initial", m.getString("shared_key"));
        assertEquals("val", m.getString("another"));
        assertEquals("third", m.getString("last"));
    }

    @Test
    void systemIsolationFromNewConfig() {
        ConfigUtil.system().loadString("sys=global");
        ConfigMap independent = ConfigUtil.newConfig().loadString("indep=own");
        assertNull(independent.getString("sys"));
        assertEquals("own", independent.getString("indep"));
    }

    @Test
    void systemReturnTypeIsConfigMap() {
        ConfigMap m = ConfigUtil.system().loadString("k=v");
        assertEquals("v", m.getString("k"));
    }
}
