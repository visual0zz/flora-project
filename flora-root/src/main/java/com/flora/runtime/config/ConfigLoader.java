package com.flora.runtime.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 配置加载器，支持从多种来源加载并合并配置。
 * <p>
 * 可创建隔离的实例，也可使用全局默认实例 {@link #system()}。
 * 加载时会自动处理配置中的 {@code flora.config.includes} 包含指令，
 * 递归加载被引用的配置文件并合并。
 * </p>
 *
 * <h3>用法</h3>
 * <pre>{@code
 * // 全局默认实例
 * ConfigLoader.system().addSource(new FileConfigSource(Paths.get("app.yaml")));
 * ConfigMap config = ConfigLoader.system().load();
 *
 * // 独立实例
 * ConfigLoader loader = new ConfigLoader();
 * loader.addSource(new ClasspathConfigSource("config/app.yaml"));
 * loader.addSource(new FileConfigSource(Paths.get("override.properties")));
 * ConfigMap cfg = loader.load();
 * }</pre>
 */
public final class ConfigLoader {

    private static final ConfigLoader SYSTEM = new ConfigLoader();
    private static final String INCLUDE_KEY = "flora.config.includes";

    private final List<ConfigSource> sources = new CopyOnWriteArrayList<>();

    /** 创建隔离的 ConfigLoader 实例（来源列表独立，不与 {@link #system()} 共享）。 */
    public ConfigLoader() {}

    /** 全局默认 ConfigLoader 实例。 */
    public static ConfigLoader system() {
        return SYSTEM;
    }

    // ====== 来源管理 ======

    /** 添加配置来源（后添加的优先级更高）。 */
    public void addSource(ConfigSource source) {
        sources.add(source);
    }

    /** 移除指定来源。 */
    public boolean removeSource(ConfigSource source) {
        return sources.remove(source);
    }

    /** 返回当前已注册的来源列表（不可变视图）。 */
    public List<ConfigSource> getSources() {
        return Collections.unmodifiableList(sources);
    }

    // ====== 加载 ======

    /**
     * 加载并合并所有已注册来源的配置，然后处理包含指令。
     *
     * @return 合并后的配置
     * @throws ConfigException 加载失败时抛出
     */
    public ConfigMap load() {
        if (sources.isEmpty()) return ConfigMap.empty();

        // 第一轮：加载所有已注册来源
        ConfigMap merged = ConfigMap.empty();
        for (ConfigSource source : sources) {
            ConfigMap cfg = source.load();
            merged = ConfigMap.merge(merged, cfg);
        }

        // 处理包含指令（支持递归）
        merged = resolveIncludes(merged, new HashSet<>());

        return merged;
    }

    /**
     * 解析配置中的 {@code flora.config.includes} 包含指令。
     * <p>每个条目可以是：
     * <ul>
     *   <li>字符串：视为文件路径，格式由扩展名推断</li>
     *   <li>映射：可含 {@code path}（路径）、{@code format}（格式名，可选）、
     *       {@code classpath}（是否从类路径加载，可选，默认 false）</li>
     * </ul>
     * </p>
     */
    @SuppressWarnings("unchecked")
    private ConfigMap resolveIncludes(ConfigMap config, Set<String> visited) {
        List<Object> includes = config.getList(INCLUDE_KEY);
        if (includes == null || includes.isEmpty()) return config;

        // 构建不含包含指令的新配置
        Map<String, Object> baseMap = new LinkedHashMap<>(config.toMap());
        baseMap.remove(INCLUDE_KEY);
        // 递归移除嵌套的包含指令（flora.config.includes 如果在各级 Map 中存在）
        baseMap.replaceAll((k, v) -> {
            if (v instanceof Map) {
                return ConfigMap.merge(ConfigMap.empty(), resolveIncludes(ConfigMap.of((Map<String, Object>) v), visited)).toMap();
            }
            return v;
        });
        ConfigMap result = ConfigMap.of(baseMap);

        for (Object include : includes) {
            ConfigMap included = loadInclude(include, visited);
            result = ConfigMap.merge(result, included);
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private ConfigMap loadInclude(Object include, Set<String> visited) {
        String path;
        boolean fromClasspath;
        ConfigFormat format;

        if (include instanceof String s) {
            if (s.startsWith("classpath:")) {
                path = s.substring("classpath:".length());
                fromClasspath = true;
            } else {
                path = s;
                fromClasspath = false;
            }
            format = ConfigFormat.fromFilename(path);
        } else if (include instanceof Map<?, ?> m) {
            Map<String, Object> entry = (Map<String, Object>) m;
            path = Objects.toString(entry.get("path"), null);
            if (path == null) throw new ConfigException("包含条目缺少 'path' 字段: " + entry);
            fromClasspath = Boolean.TRUE.equals(entry.get("classpath"));
            Object fmtObj = entry.get("format");
            format = fmtObj != null ? ConfigFormat.valueOf(fmtObj.toString().toUpperCase()) : ConfigFormat.fromFilename(path);
        } else {
            throw new ConfigException("无效的包含条目类型: " + include.getClass().getName());
        }

        // 防循环
        String visitKey = (fromClasspath ? "cp:" : "file:") + path;
        if (!visited.add(visitKey)) {
            throw new ConfigException("检测到配置包含循环: " + visitKey);
        }

        ConfigSource source;
        if (fromClasspath) {
            source = new ClasspathConfigSource(path);
        } else {
            // 如果是相对路径，尝试基于已有来源的目录解析
            Path filePath = Paths.get(path);
            if (!filePath.isAbsolute()) {
                filePath = resolveRelativePath(path);
            }
            source = new FileConfigSource(filePath, format);
        }

        ConfigMap cfg = source.load();
        // 递归处理被包含配置中的包含指令
        ConfigMap resolved = resolveIncludes(cfg, visited);

        visited.remove(visitKey);
        return resolved;
    }

    /** 尝试根据已注册文件来源的目录解析相对路径。 */
    private Path resolveRelativePath(String path) {
        for (ConfigSource src : sources) {
            if (src instanceof FileConfigSource) {
                Path baseDir = Paths.get(src.location()).getParent();
                if (baseDir != null) {
                    return baseDir.resolve(path).normalize();
                }
            }
        }
        return Paths.get(path).toAbsolutePath().normalize();
    }
}
