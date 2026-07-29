package com.flora.runtime.config;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 配置加载器，支持从多种来源加载并合并配置。
 * <p>
 * 可创建隔离的实例，也可使用全局默认实例 {@link #system()}。
 * </p>
 *
 * <h3>基础用法：单次加载</h3>
 * <pre>{@code
 * ConfigLoader loader = new ConfigLoader();
 * loader.addSource(new FileConfigSource(Paths.get("app.yaml")));
 * ConfigMap config = loader.load();
 * }</pre>
 *
 * <h3>分阶段加载（Java 编排——无预留命名空间）</h3>
 * <p>先加载初始配置，由 Java 代码读取已加载配置中的普通 key 的值，
 * 决定下一步加载哪些配置文件，重复此过程直至完成。</p>
 * <pre>{@code
 * ConfigMap config = loader.resolve(cfg -> {
 *     String dbPath = cfg.getString("database.config");   // 普通 app key，非预留
 *     if (dbPath == null) return Collections.emptyList();
 *     return List.of(new FileConfigSource(Paths.get(dbPath)));
 * });
 * }</pre>
 *
 * <p>每次 {@code resolve} 的回调接收当前已合并的配置，返回待加载的额外来源；
 * 加载器自动合并并反复调用回调，直到回调返回空列表或检测到循环为止。</p>
 */
public final class ConfigLoader {

    private static final ConfigLoader SYSTEM = new ConfigLoader();

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

    // ====== 单次加载 ======

    /**
     * 加载并合并所有已注册来源的配置。
     *
     * @return 合并后的配置
     * @throws ConfigException 加载失败时抛出
     */
    public ConfigMap load() {
        if (sources.isEmpty()) return ConfigMap.empty();
        ConfigMap merged = ConfigMap.empty();
        for (ConfigSource source : sources) {
            ConfigMap cfg = source.load();
            merged = ConfigMap.merge(merged, cfg);
        }
        return merged;
    }

    // ====== 分阶段加载（Java 编排） ======

    /**
     * 分阶段加载配置。先加载所有已注册来源，然后反复调用回调，
     * 由回调从已加载的配置中读取普通 key 的值来确定下一步加载哪些额外来源，
     * 直到回调返回空列表为止。
     * <p>自动检测循环：同一来源位置不会被重复加载。</p>
     *
     * @param resolver 回调函数，接收当前合并后的配置，返回下一步要加载的来源列表
     * @return 最终合并后的配置
     */
    public ConfigMap resolve(ConfigResolver resolver) {
        // 第一轮：加载所有已注册来源
        ConfigMap merged = load();
        Set<String> seen = new HashSet<>();
        for (ConfigSource src : sources) {
            String loc = src.location();
            if (loc != null) seen.add(loc);
        }

        int maxRounds = 100;
        for (int round = 0; round < maxRounds; round++) {
            List<ConfigSource> additional = resolver.resolve(merged);
            if (additional == null || additional.isEmpty()) break;

            // 过滤已加载过的来源
            List<ConfigSource> newSources = new ArrayList<>();
            for (ConfigSource src : additional) {
                String loc = src.location();
                if (loc != null && !seen.add(loc)) {
                    continue; // 循环保护
                }
                newSources.add(src);
                sources.add(src);
            }
            if (newSources.isEmpty()) break;

            // 加载新来源并合并
            for (ConfigSource src : newSources) {
                ConfigMap cfg = src.load();
                merged = ConfigMap.merge(merged, cfg);
            }
        }
        return merged;
    }

    /**
     * 配置解析器接口——由用户实现，从已加载的配置中读取普通 key 的值，
     * 返回下一步要加载的额外配置来源。
     */
    @FunctionalInterface
    public interface ConfigResolver {
        /**
         * 根据当前已合并的配置决定加载哪些额外来源。
         *
         * @param currentConfig 当前已合并的配置
         * @return 待加载的额外来源列表；返回 null 或空列表表示结束
         */
        List<ConfigSource> resolve(ConfigMap currentConfig);
    }
}
