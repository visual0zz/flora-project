package com.flora.runtime.config;

import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 流式配置构建器，同时也是 {@link Config}。
 * <p>通过 {@link ConfigUtil#newConfig()} 或 {@link ConfigUtil#system()} 获得实例。
 * 链式调用的结果直接可作为 {@code Config} 使用，无需额外操作。</p>
 *
 * <pre>{@code
 * Config config = ConfigChain.newConfig()
 *     .load(new FileConfigSource(Paths.get("base.yaml")))
 *     .loadFile("override.yaml")
 *     .loadString("key=val");
 * }</pre>
 *
 * <p>通用方法 {@link #load(ConfigSource)} 接受任意来源，
 * {@link #loadFile} / {@link #loadString} 是它的语法糖。
 * 占位符 {@code {key}} 从当前已加载的配置中取值。</p>
 */
public final class ConfigChain extends Config {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^}]+)\\}");

    private final ConfigLoader loader;

    /** 包内使用：由 {@link ConfigUtil#newConfig()} 或 {@link ConfigUtil#system()} 创建。 */
    public ConfigChain(ConfigLoader loader) {
        super(loader.load().toMap());
        this.loader = loader;
    }

    // ====== 通用入口 ======

    /**
     * 添加任意配置来源并返回最新快照。
     * <p>{@link #loadFile} 和 {@link #loadString} 均委托至此方法。</p>
     *
     * @param source 配置来源（可实现 {@link ConfigSource} 自定义）
     * @return 加载了当前所有来源的新快照
     */
    public ConfigChain load(ConfigSource source) {
        loader.addSource(source);
        return new ConfigChain(loader);
    }

    // ====== 语法糖 ======

    /** 从文件加载，等价于 {@code load(new FileConfigSource(...))}。 */
    public ConfigChain loadFile(String path) {
        String resolved = resolvePlaceholders(path);
        if (resolved.startsWith("classpath:")) {
            return load(new ClasspathConfigSource(
                    resolved.substring("classpath:".length())));
        }
        return load(new FileConfigSource(Paths.get(resolved)));
    }

    /** 从 Properties 字符串加载，等价于 {@code load(new StringConfigSource(PROPS, ...))}。 */
    public ConfigChain loadString(String content) {
        String resolved = resolvePlaceholders(content);
        String label = "<inline>:" + (content.length() > 40
                ? content.substring(0, 37) + "..." : content);
        return load(new StringConfigSource(ConfigFormat.PROPERTIES, resolved, label));
    }

    // ====== 占位符解析 ======

    private String resolvePlaceholders(String input) {
        if (input == null || input.isEmpty()) return input;
        Matcher m = PLACEHOLDER.matcher(input);
        if (!m.find()) return input;
        StringBuilder sb = new StringBuilder();
        m.reset();
        while (m.find()) {
            String key = m.group(1);
            String value = getString(key);
            if (value == null) {
                throw new ConfigException("无法解析占位符: " + m.group(0)
                        + "（当前配置中不存在键: " + key + "）");
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
