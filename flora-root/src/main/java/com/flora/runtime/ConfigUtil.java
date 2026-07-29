package com.flora.runtime;

import com.flora.runtime.config.*;

import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 配置加载的流式入口，提供便捷的链式 API。
 * <p>{@link ConfigChain} 继承 {@link Config}，链式调用的结果直接可作为
 * {@code Config} 使用，无需额外的 {@code .load()} 调用。</p>
 *
 * <h3>两种模式</h3>
 * <ul>
 *   <li>{@link #newConfig()} — 创建独立的配置加载器，互不干扰</li>
 *   <li>{@link #system()} — 使用全局单例，所有调用共享同一来源列表</li>
 * </ul>
 *
 * <h3>用法</h3>
 * <pre>{@code
 * // 独立配置——`loadFile` 是 `load(new FileConfigSource(...))` 的语法糖
 * Config config = ConfigUtil
 *     .newConfig()
 *     .load(new FileConfigSource(Paths.get("base.yaml")))
 *     .loadFile("override.yaml")
 *     .loadString("key=val");
 *
 * // 自定义来源
 * Config config = ConfigUtil.newConfig()
 *     .load(new MyCustomSource(...))
 *     .loadFile("extra.yaml");
 * }</pre>
 *
 * <p>占位符 {@code {key}} 从当前已加载的配置中取值。
 * 路径以 {@code classpath:} 开头时从类路径加载。</p>
 */
public final class ConfigUtil {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^}]+)\\}");

    private ConfigUtil() {}

    // ====== 静态入口 ======

    /** 创建一个新的独立配置链（底层为全新的 {@link ConfigLoader}）。 */
    public static ConfigChain newConfig() {
        return new ConfigChain(new ConfigLoader());
    }

    /** 使用全局单例 {@link ConfigLoader#system()}，多处调用共享同一来源列表。 */
    public static ConfigChain system() {
        return new ConfigChain(ConfigLoader.system());
    }

    // ====== 链式构建器 ======

    /**
     * 流式配置构建器，同时也是 {@link Config}。
     * <p>通过 {@link #newConfig()} 或 {@link #system()} 获得实例。</p>
     *
     * <p>通用方法 {@link #load(ConfigSource)} 接受任意来源，
     * {@link #loadFile} / {@link #loadString} 是它的语法糖。</p>
     */
    public static final class ConfigChain extends Config {

        private final ConfigLoader loader;

        ConfigChain(ConfigLoader loader) {
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

        /** 返回当前合并快照（供非链式场景使用）。 */
        public Config load() {
            return loader.load();
        }

        // ====== 语法糖 ======

        /**
         * 从文件加载配置，等价于 {@code load(new FileConfigSource(...))}。
         * <p>路径中的 {@code {key}} 占位符从当前已加载配置中取值。
         * 以 {@code classpath:} 开头时从类路径加载。</p>
         */
        public ConfigChain loadFile(String path) {
            String resolved = resolvePlaceholders(path, this);
            if (resolved.startsWith("classpath:")) {
                return load(new ClasspathConfigSource(
                        resolved.substring("classpath:".length())));
            }
            return load(new FileConfigSource(Paths.get(resolved)));
        }

        /**
         * 从 Properties 格式字符串加载配置，等价于
         * {@code load(new StringConfigSource(PROPERTIES, content))}。
         * <p>内容中的 {@code {key}} 占位符从当前已加载配置中取值。</p>
         */
        public ConfigChain loadString(String content) {
            String resolved = resolvePlaceholders(content, this);
            String label = "<inline>:" + (content.length() > 40
                    ? content.substring(0, 37) + "..." : content);
            return load(new StringConfigSource(ConfigFormat.PROPERTIES, resolved, label));
        }
    }

    // ====== 占位符解析 ======

    /**
     * 将 {@code {key}} 从当前已加载的配置中取值替换。
     *
     * @param input  含占位符的字符串
     * @param config 当前已加载的配置
     * @return 替换后的字符串
     */
    static String resolvePlaceholders(String input, Config config) {
        if (input == null || input.isEmpty()) return input;
        Matcher m = PLACEHOLDER.matcher(input);
        if (!m.find()) return input;
        StringBuilder sb = new StringBuilder();
        m.reset();
        while (m.find()) {
            String key = m.group(1);
            String value = config.getString(key);
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
