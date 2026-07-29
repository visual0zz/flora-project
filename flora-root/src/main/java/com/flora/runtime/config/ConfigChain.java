package com.flora.runtime.config;

import com.flora.runtime.config.source.*;

import java.nio.file.Paths;

/**
 * 流式配置构建器，同时也是 {@link Config}。
 * <p>通过 {@link com.flora.runtime.ConfigUtil#newConfig()} 或 {@link com.flora.runtime.ConfigUtil#system()} 获得实例。
 * 链式调用的结果直接可作为 {@code Config} 使用。</p>
 */
public final class ConfigChain extends Config {

    private final ConfigLoader loader;

    /** 包内使用：由 {@link com.flora.runtime.ConfigUtil#newConfig()} 创建。 */
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
