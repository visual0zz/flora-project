package com.flora.runtime.config;

import com.flora.runtime.config.impl.ConfigLoader;
import com.flora.runtime.config.impl.ClasspathConfigSource;
import com.flora.runtime.config.impl.FileConfigSource;
import com.flora.runtime.config.impl.StringConfigSource;

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

    /**
     * 流式配置构建器，同时也是 {@link Config}。
     * <p>通过 {@link ConfigUtil#newConfig()} 或 {@link ConfigUtil#system()} 获得实例。
     * 链式调用的结果直接可作为 {@code Config} 使用。</p>
     */
    public static final class ConfigChain extends Config {

        private final ConfigLoader loader;

        /** 包内使用：由 {@link ConfigUtil#newConfig()} 创建。 */
        public ConfigChain(ConfigLoader loader) {
            super(loader.load().toMap());
            this.loader = loader;
        }

        /** 添加任意配置来源并返回最新快照。 */
        public ConfigChain load(ConfigSource source) {
            loader.addSource(source);
            return new ConfigChain(loader);
        }

        /** 从文件加载配置，等价于 {@code load(new FileConfigSource(...))}。 */
        public ConfigChain loadFile(String path) {
            if (path.startsWith("classpath:")) {
                return load(new ClasspathConfigSource(path.substring("classpath:".length())));
            }
            return load(new FileConfigSource(Paths.get(path)));
        }

        /** 从 Properties 字符串加载配置，等价于 {@code load(new StringConfigSource(PROPS, ...))}。 */
        public ConfigChain loadString(String content) {
            String label = "<inline>:" + (content.length() > 40
                    ? content.substring(0, 37) + "..." : content);
            return load(new StringConfigSource(ConfigFormat.PROPERTIES, content, label));
        }
    }
}
