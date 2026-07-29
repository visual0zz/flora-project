package com.flora.runtime.config;

import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 配置加载的流式入口，提供便捷的链式 API。
 *
 * <h3>两种模式</h3>
 * <ul>
 *   <li>{@link #system()} — 加载到全局单例 {@link ConfigLoader#system()}，所有调用共享</li>
 *   <li>{@link #newConfig()} — 创建独立的配置加载器，互不干扰</li>
 * </ul>
 *
 * <h3>用法</h3>
 * <pre>{@code
 * // 独立配置
 * ConfigMap config = ConfigUtil
 *     .newConfig()
 *     .loadFile("config/base.yaml")
 *     .loadFile("{app.home}/override.yaml")
 *     .loadString("com.flora.name=zz")
 *     .load();
 *
 * // 全局单例
 * ConfigUtil.system()
 *     .loadFile("config/defaults.yaml")
 *     .load();
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

    /** 从文件加载独立配置，等价于 {@code newConfig().loadFile(path)}。 */
    public static ConfigChain loadFile(String path) {
        return newConfig().loadFile(path);
    }

    /** 从 Properties 格式字符串加载独立配置，等价于 {@code newConfig().loadString(content)}。 */
    public static ConfigChain loadString(String content) {
        return newConfig().loadString(content);
    }

    // ====== 链式构建器 ======

    /**
     * 流式配置构建器。通过 {@link ConfigUtil#newConfig()} 或
     * {@link ConfigUtil#system()} 获得实例。
     */
    public static final class ConfigChain {

        private final ConfigLoader loader;

        ConfigChain(ConfigLoader loader) {
            this.loader = loader;
        }

        /** 添加文件来源。路径中的 {@code {key}} 占位符会被解析。 */
        public ConfigChain loadFile(String path) {
            String resolved = resolve(path);
            if (resolved.startsWith("classpath:")) {
                loader.addSource(new ClasspathConfigSource(resolved.substring("classpath:".length())));
            } else {
                loader.addSource(new FileConfigSource(Paths.get(resolved)));
            }
            return this;
        }

        /** 添加 Properties 格式的字符串来源。内容中的 {@code {key}} 占位符会被解析。 */
        public ConfigChain loadString(String content) {
            String resolved = resolve(content);
            String label = "<inline>:" + (content.length() > 40 ? content.substring(0, 37) + "..." : content);
            loader.addSource(new StringConfigSource(ConfigFormat.PROPERTIES, resolved, label));
            return this;
        }

        /** 执行加载，返回合并后的配置。 */
        public ConfigMap load() {
            return loader.load();
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
}
