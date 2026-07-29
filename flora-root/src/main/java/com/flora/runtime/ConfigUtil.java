package com.flora.runtime;

import com.flora.runtime.config.ConfigChain;
import com.flora.runtime.config.ConfigLoader;

/**
 * 配置加载的流式入口。
 *
 * <pre>{@code
 * Config config = ConfigUtil.newConfig()
 *     .load(new FileConfigSource(Paths.get("base.yaml")))
 *     .loadFile("override.yaml")
 *     .loadString("key=val");
 * }</pre>
 */
public final class ConfigUtil {

    private ConfigUtil() {}

    /** 创建独立配置链。 */
    public static ConfigChain newConfig() {
        return new ConfigChain(new ConfigLoader());
    }

    /** 使用全局单例，多处调用共享同一来源列表。 */
    public static ConfigChain system() {
        return new ConfigChain(ConfigLoader.system());
    }
}
