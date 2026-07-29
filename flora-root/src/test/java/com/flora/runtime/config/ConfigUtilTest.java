package com.flora.runtime.config;

import com.flora.runtime.ConfigUtil;
import com.flora.runtime.config.source.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ConfigUtil} 流式配置 API 的单元测试。
 */
class ConfigUtilTest {

    @AfterEach
    void cleanSystem() {
        ConfigLoader.system().clearSources();
    }

    // ====== 通用 load(ConfigSource) ======

    @Test
    void loadGenericSource() {
        Config config = ConfigUtil.newConfig()
                .load(new StringConfigSource(ConfigFormat.PROPERTIES, "k=hello"));
        assertEquals("hello", config.getString("k"));
    }

    @Test
    void loadFileSourceViaGeneric() {
        Config config = ConfigUtil.newConfig()
                .load(new FileConfigSource(Paths.get("src/test/resources/config/app.yaml")));
        assertEquals("test-app", config.getString("app.name"));
    }

    // ====== loadFile 语法糖 ======

    @Test
    void loadFile() {
        Config config = ConfigUtil.newConfig()
                .loadFile("src/test/resources/config/app.yaml");
        assertEquals("test-app", config.getString("app.name"));
    }

    // ====== loadString 语法糖 ======

    @Test
    void loadString() {
        Config config = ConfigUtil.newConfig()
                .loadString("name=hello\ncount=42");
        assertEquals("hello", config.getString("name"));
        assertEquals("42", config.getString("count"));
    }

    // ====== 链式混合 ======

    @Test
    void chainedMerge() {
        Config config = ConfigUtil.newConfig()
                .loadString("key=first\nshared=from_first")
                .loadString("key=second\nshared=from_second");
        assertEquals("second", config.getString("key"));
        assertEquals("from_second", config.getString("shared"));
    }

    @Test
    void chainGenericAndSugar() {
        Config config = ConfigUtil.newConfig()
                .load(new StringConfigSource(ConfigFormat.PROPERTIES, "name=default"))
                .loadFile("src/test/resources/config/app.yaml");
        assertEquals("test-app", config.getString("app.name"));
    }

    @Test
    void classpathPrefix() {
        Config config = ConfigUtil.newConfig()
                .loadFile("classpath:config/app.yaml");
        assertEquals("test-app", config.getString("app.name"));
    }

    // ====== 独立实例 ======

    @Test
    void newConfigIsIndependent() {
        Config c1 = ConfigUtil.newConfig().loadString("k=from_first");
        Config c2 = ConfigUtil.newConfig().loadString("k=from_second");
        assertEquals("from_first", c1.getString("k"));
        assertEquals("from_second", c2.getString("k"));
    }

    // ====== 全局单例 ======

    @Test
    void systemSharedAcrossCalls() {
        ConfigUtil.system().loadString("shared_key=initial");
        ConfigUtil.system().loadString("another=val");
        Config config = ConfigUtil.system().loadString("last=third");
        assertEquals("initial", config.getString("shared_key"));
        assertEquals("val", config.getString("another"));
        assertEquals("third", config.getString("last"));
    }

    @Test
    void systemIsolationFromNewConfig() {
        ConfigUtil.system().loadString("sys=global");
        Config independent = ConfigUtil.newConfig().loadString("indep=own");
        assertNull(independent.getString("sys"));
        assertEquals("own", independent.getString("indep"));
    }

    @Test
    void systemReturnTypeIsConfig() {
        Config config = ConfigUtil.system().loadString("k=v");
        assertEquals("v", config.getString("k"));
    }

    // ====== 自定义 ConfigSource ======

    @Test
    void customConfigSource() {
        ConfigSource custom = new ConfigSource() {
            @Override public Config load() {
                return Config.of(java.util.Map.of("from", "custom"));
            }
            @Override public String describe() { return "custom"; }
            @Override public String location() { return "custom"; }
        };
        Config config = ConfigUtil.newConfig().load(custom);
        assertEquals("custom", config.getString("from"));
    }
}
