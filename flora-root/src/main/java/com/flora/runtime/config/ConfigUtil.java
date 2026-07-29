package com.flora.runtime.config;

import java.nio.file.Paths;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 配置加载的流式入口，提供便捷的链式 API。
 * <p>{@link ConfigChain} 继承 {@link ConfigMap}，链式调用的结果直接可作为
 * {@code ConfigMap} 使用，无需额外的 {@code .load()} 调用。</p>
 *
 * <h3>两种模式</h3>
 * <ul>
 *   <li>{@link #newConfig()} — 创建独立的配置加载器，互不干扰</li>
 *   <li>{@link #system()} — 使用全局单例，所有调用共享同一来源列表</li>
 * </ul>
 *
 * <h3>用法</h3>
 * <pre>{@code
 * // 独立配置——结果直接是 ConfigMap，无需 .load()
 * ConfigMap config = ConfigUtil
 *     .newConfig()
 *     .loadFile("config/base.yaml")
 *     .loadFile("{app.home}/override.yaml")
 *     .loadString("com.flora.name=zz");
 *
 * // 全局单例
 * ConfigUtil.system().loadFile("config/defaults.yaml");
 * ConfigMap global = ConfigUtil.system().loadFile("override.yaml");
 * }</pre>
 *
 * <p>占位符 {@code {key}} 依次从 {@link System#getProperty(String)} 和
 * {@link System#getenv(String)} 解析。路径以 {@code classpath:} 开头时
 * 从类路径加载。</p>
 */
public final class ConfigUtil {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^}]+)\\}");

    private ConfigUtil() {}

    // ====== 静态入口 ======

    /** 创建一个新的独立配置链（底层为全新的 {@link ConfigLoader}）。 */
    public static ConfigChain newConfig() {
        return new ConfigChain(new ConfigLoader());
    }

    /** 使用全局单例 {@link ConfigLoader#system()}，多處调用共享同一来源列表。 */
    public static ConfigChain system() {
        return new ConfigChain(ConfigLoader.system());
    }

    // ====== 链式构建器（继承 ConfigMap） ======

    /**
     * 流式配置构建器，同时也是 {@link ConfigMap}。
     * <p>通过 {@link #newConfig()} 或 {@link #system()} 获得实例后，
     * 调用 {@link #loadFile} / {@link #loadString} 添加来源，
     * 每个调用返回加载了当前所有来源的新 {@code ConfigChain}
     * （即 {@code ConfigMap}），可直接使用。</p>
     */
    public static final class ConfigChain extends ConfigMap {

        private final ConfigLoader loader;

        ConfigChain(ConfigLoader loader) {
            super(loader.load().toMap());
            this.loader = loader;
        }

        /** 加载并返回一个新的合并快照（供内部或非链式场景使用）。 */
        public ConfigMap load() {
            return loader.load();
        }

        /** 添加文件来源。路径中的 {@code {key}} 占位符会被解析。 */
        public ConfigChain loadFile(String path) {
            String resolved = resolve(path);
            if (resolved.startsWith("classpath:")) {
                loader.addSource(new ClasspathConfigSource(resolved.substring("classpath:".length())));
            } else {
                loader.addSource(new FileConfigSource(Paths.get(resolved)));
            }
            return new ConfigChain(loader);
        }

        /** 添加 Properties 格式的字符串来源。内容中的 {@code {key}} 占位符会被解析。 */
        public ConfigChain loadString(String content) {
            String resolved = resolve(content);
            String label = "<inline>:" + (content.length() > 40 ? content.substring(0, 37) + "..." : content);
            loader.addSource(new StringConfigSource(ConfigFormat.PROPERTIES, resolved, label));
            return new ConfigChain(loader);
        }
    }

    // ====== 占位符解析 ======

    /** 将字符串中的 {@code {key}} 替换为系统属性或环境变量值。 */
    static String resolve(String input) {
        if (input == null || input.isEmpty()) return input;
        Matcher m = PLACEHOLDER.matcher(input);
        if (!m.find()) return input;
        StringBuilder sb = new StringBuilder();
        m.reset();
        while (m.find()) {
            String key = m.group(1);
            String value = System.getProperty(key);
            if (value == null) {
                value = System.getenv(key);
            }
            if (value == null) {
                throw new ConfigException("无法解析占位符: " + m.group(0)
                        + "（未找到系统属性或环境变量: " + key + "）");
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
