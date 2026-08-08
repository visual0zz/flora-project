package com.flora.runtime.config;

import com.flora.runtime.config.interfaces.Config;
import com.flora.runtime.config.interfaces.ConfigSource;
import com.flora.tag.ModuleEntry;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 配置加载的流式入口。
 *
 * <pre>{@code
 * Config config = ConfigUtil.newConfig()
 *     .load(new FileConfigSource(...))
 *     .loadFile("override.yaml")
 *     .loadString("key=val")
 *     .getMap("database");
 * }</pre>
 */
@ModuleEntry
public final class ConfigUtil {

    private ConfigUtil() {}

    /** 创建独立配置链。 */
    public static ConfigBuilder newConfig() {
        return new ConfigBuilder();
    }

    /** 使用全局单例，多处调用共享同一来源列表。 */
    public static ConfigBuilder system() {
        return new ConfigBuilder();
    }

    /**
     * 流式配置构建器，同时也是 {@link Config}。
     * <p>通过 {@link ConfigUtil#newConfig()} 或 {@link ConfigUtil#system()} 获得实例。
     * 链式调用的结果直接可作为 {@code Config} 使用。</p>
     */
    public static final class ConfigBuilder{
        void loadFrom(ConfigSource source) {
            //todo
        }
        void loadFromFile(Path filePath){

        }

    }
}
