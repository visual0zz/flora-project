package com.flora.runtime.config;

import com.flora.runtime.config.interfaces.Config;
import com.flora.runtime.config.interfaces.ConfigView;
import com.flora.runtime.config.interfaces.ReloadableConfig;
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
        ConfigUtil.replaceSystem().flush();   // 清空全局单例，避免测试间污染
    }

    // ====== newConfig：构建静态 Config ======

    @Test
    void newConfigBuildFromString() {
        Config config = ConfigUtil.newConfig().loadFromString("name=hello").build();
        assertEquals("hello", config.get("name"));
    }

    @Test
    void newConfigBuildFromFile() {
        Config config = ConfigUtil.newConfig()
                .loadFromFile(Paths.get("src/test/resources/config/app.yaml"))
                .build();
        assertEquals("test-app", config.get("app.name"));
    }

    @Test
    void newConfigChainedMergeLaterOverrides() {
        Config config = ConfigUtil.newConfig()
                .loadFromString("key=first\nshared=from_first")
                .loadFromString("key=second\nshared=from_second")
                .build();
        assertEquals("second", config.get("key"));
        assertEquals("from_second", config.get("shared"));
    }

    @Test
    void newConfigPriorityHigherOverridesLower() {
        Config config = ConfigUtil.newConfig()
                .loadFromString(ConfigPriority.LOW, "k=low")
                .loadFromString(ConfigPriority.HIGH, "k=high")
                .build();
        assertEquals("high", config.get("k"));
    }

    @Test
    void newConfigViewReturnsConfigView() {
        // view() 返回轻量 ConfigView（仅 get 下钻），类型化读取用 FluentConfigWrapper.of(build())
        ConfigView view = ConfigUtil.newConfig()
                .loadFromString("port=3306")
                .view();
        assertEquals("3306", view.get("port"));
    }

    @Test
    void newConfigSubConfigPromotion() {
        Config config = ConfigUtil.newConfig()
                .loadFromString("com.flora.database.host=127.0.0.1\ndatabase.host=192.168.0.1")
                .loadFromSubConfig("com.flora")
                .build();
        // com.flora 下的子树提升覆盖顶层 database
        assertEquals("127.0.0.1", config.get("database.host"));
    }

    // ====== newConfig().buildReloadable()：构建新 ReloadableConfig ======

    @Test
    void newConfigBuild() {
        ReloadableConfig r = ConfigUtil.newConfig()
                .loadFromString("k=v")
                .buildReloadable();
        assertEquals("v", r.get("k"));
    }

    // ====== replaceConfig / refreshConfig：更新既有目标 ======

    @Test
    void replaceConfigReplacesWhole() {
        ReloadableConfig r = ConfigUtil.newConfig().loadFromString("a=1\nb=2").buildReloadable();
        ConfigUtil.replaceConfig(r).loadFromString("b=3").flush();
        assertNull(r.get("a"));          // 未在新配置中 -> 被替换掉
        assertEquals("3", r.get("b"));
    }

    @Test
    void refreshConfigMergesKeepingOld() {
        ReloadableConfig r = ConfigUtil.newConfig().loadFromString("a=1\nb=2").buildReloadable();
        ConfigUtil.refreshConfig(r).loadFromString("b=3").flush();
        assertEquals("1", r.get("a"));   // 无新值 -> 保留
        assertEquals("3", r.get("b"));   // 新值覆盖
    }

    @Test
    void replaceConfigNullTargetThrows() {
        assertThrows(ConfigException.class, () -> ConfigUtil.replaceConfig(null));
        assertThrows(ConfigException.class, () -> ConfigUtil.refreshConfig(null));
    }

    // ====== 运行期防御（编译期已由 ConfigBuilder/ConfigUpdater 接口限制，此处验证强转绕过仍被拦截） ======

    @Test
    void boundHelperCannotBuildEvenWhenCast() {
        ReloadableConfig r = ConfigUtil.newConfig().buildReloadable();
        ConfigUtil.ConfigLoadHelper helper = (ConfigUtil.ConfigLoadHelper) ConfigUtil.replaceConfig(r);
        assertThrows(IllegalStateException.class, helper::build);
        assertThrows(IllegalStateException.class, helper::buildReloadable);
    }

    @Test
    void unboundHelperCannotFlushEvenWhenCast() {
        ConfigUtil.ConfigLoadHelper helper = (ConfigUtil.ConfigLoadHelper) ConfigUtil.newConfig();
        assertThrows(IllegalStateException.class, helper::flush);
    }

    // ====== system：全局单例替换式更新 ======

    @Test
    void systemReplacesGlobally() {
        ConfigUtil.replaceSystem().loadFromString("sys=one").flush();
        assertEquals("one", ConfigUtil.systemConfig().get("sys"));

        ConfigUtil.replaceSystem().loadFromString("sys=two").flush();
        assertEquals("two", ConfigUtil.systemConfig().get("sys"));
    }

    @Test
    void systemIsolationFromNewConfig() {
        ConfigUtil.replaceSystem().loadFromString("sys=global").flush();
        Config independent = ConfigUtil.newConfig().loadFromString("indep=own").build();
        assertNull(independent.get("sys"));
        assertEquals("own", independent.get("indep"));
    }

    @Test
    void refreshSystemMergesKeepingOld() {
        ConfigUtil.replaceSystem().loadFromString("keep=old\nover=old").flush();
        ConfigUtil.refreshSystem().loadFromString("over=new").flush();
        assertEquals("old", ConfigUtil.systemConfig().get("keep"));  // 无新值 -> 保留
        assertEquals("new", ConfigUtil.systemConfig().get("over"));  // 新值覆盖
    }

    // ====== 占位符 ======

    @Test
    void placeholderResolvedInBuild() {
        Config config = ConfigUtil.newConfig()
                .loadFromString("db.host=localhost\ndb.port=3306\ndb.url=jdbc://${db.host}:${db.port}")
                .build();
        assertEquals("jdbc://localhost:3306", config.get("db.url"));
    }

    @Test
    void placeholderMissingKeyThrows() {
        assertThrows(ConfigException.class,
                () -> ConfigUtil.newConfig().loadFromString("a=${missing}").build());
    }

    @Test
    void placeholderFromSystemProperty() {
        Config config = ConfigUtil.newConfig().loadFromString("v=${java.version}").build();
        assertEquals(System.getProperty("java.version"), config.get("v"));
    }

    @Test
    void placeholderCircularThrows() {
        assertThrows(ConfigException.class,
                () -> ConfigUtil.newConfig().loadFromString("a=${b}\nb=${a}").build());
    }

    @Test
    void viewInterpretsPlaceholderOnAccess() {
        Object v = ConfigUtil.newConfig()
                .loadFromString("a=1\nb=val-${a}")
                .view()
                .get("b");
        assertEquals("val-1", v);
    }

    // ====== classpath 来源 ======

    @Test
    void loadFromClasspathResource() {
        Config config = ConfigUtil.newConfig().loadFromClasspath("config/app.yaml").build();
        assertEquals("test-app", config.get("app.name"));
    }
}
