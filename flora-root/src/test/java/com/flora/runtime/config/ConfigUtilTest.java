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
    void loadFileSingle() {
        ConfigMap m = ConfigUtil.loadFile("src/test/resources/config/app.yaml").load();
        assertEquals("test-app", m.getString("app.name"));
        assertEquals(Long.valueOf(8080), m.get("server.port"));
    }

    @Test
    void loadStringProperties() {
        ConfigMap m = ConfigUtil.loadString("name=hello\ncount=42").load();
        assertEquals("hello", m.getString("name"));
        assertEquals("42", m.getString("count")); // properties 叶子值为 String
    }

    @Test
    void chainedMerge() {
        ConfigMap m = ConfigUtil
                .loadString("key=first\nshared=from_first")
                .loadString("key=second\nshared=from_second")
                .load();
        assertEquals("second", m.getString("key"));       // 后加载覆盖
        assertEquals("from_second", m.getString("shared"));
    }

    @Test
    void chainWithFileAndString() {
        ConfigMap m = ConfigUtil
                .loadString("app.name=override")
                .loadFile("src/test/resources/config/app.yaml")
                .load();
        // app.yaml 后加载，覆盖 loadString 中的 app.name
        assertEquals("test-app", m.getString("app.name"));
    }

    @Test
    void placeholderResolvedFromSystemProperty() {
        String key = "test.config.path." + System.nanoTime();
        System.setProperty(key, "src/test/resources/config/app.yaml");
        try {
            ConfigMap m = ConfigUtil.loadFile("{" + key + "}").load();
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
            ConfigMap m = ConfigUtil.loadFile("{" + key + "}/app.yaml").load();
            assertEquals("test-app", m.getString("app.name"));
        } finally {
            System.clearProperty(key);
        }
    }

    @Test
    void placeholderNotFoundThrows() {
        assertThrows(ConfigException.class, () ->
                ConfigUtil.loadFile("{nonexistent.key}").load());
    }

    @Test
    void classpathPrefix() {
        ConfigMap m = ConfigUtil.loadFile("classpath:config/app.yaml").load();
        assertEquals("test-app", m.getString("app.name"));
    }

    @Test
    void multiplePlaceholders() {
        String key1 = "test.p1." + System.nanoTime();
        String key2 = "test.p2." + System.nanoTime();
        System.setProperty(key1, "src/test");
        System.setProperty(key2, "config/app.yaml");
        try {
            ConfigMap m = ConfigUtil.loadFile("{" + key1 + "}/resources/{" + key2 + "}").load();
            assertEquals("test-app", m.getString("app.name"));
        } finally {
            System.clearProperty(key1);
            System.clearProperty(key2);
        }
    }

    // ====== newConfig 独立实例 ======

    @Test
    void newConfigIsIndependent() {
        ConfigMap m1 = ConfigUtil.newConfig().loadString("k=from_first").load();
        ConfigMap m2 = ConfigUtil.newConfig().loadString("k=from_second").load();
        // 互不干扰
        assertEquals("from_first", m1.getString("k"));
        assertEquals("from_second", m2.getString("k"));
    }

    @Test
    void newConfigWithFile() {
        ConfigMap m = ConfigUtil.newConfig()
                .loadFile("src/test/resources/config/app.yaml")
                .load();
        assertEquals("test-app", m.getString("app.name"));
    }

    // ====== system 全局单例 ======

    @Test
    void systemSharedAcrossCalls() {
        // 第一次调用添加来源
        ConfigUtil.system().loadString("shared_key=initial").load();
        // 第二次调用添加更多来源，包含之前的
        ConfigUtil.system().loadString("another=val").load();
        ConfigMap m = ConfigUtil.system().load();
        assertEquals("initial", m.getString("shared_key"));
        assertEquals("val", m.getString("another"));
    }

    @Test
    void systemIsolationFromNewConfig() {
        // system() 添加来源
        ConfigUtil.system().loadString("sys=global").load();
        // newConfig() 不受影响
        ConfigMap independent = ConfigUtil.newConfig().loadString("indep=own").load();
        assertNull(independent.getString("sys")); // 没有 system 中的来源
        assertEquals("own", independent.getString("indep"));
    }
}
