package com.flora.runtime.config;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 配置加载器，支持从多种来源加载并合并配置。
 * <p>
 * 可创建隔离的实例，也可使用全局默认实例 {@link #system()}。
 * </p>
 *
 * <h3>优先级规则</h3>
 * <p>加载时按 {@link ConfigPriority} 从低到高依次合并，高优先级覆盖低优先级。
 * 同一优先级内，后添加的来源覆盖先添加的。</p>
 * <pre>{@code
 * ConfigLoader loader = new ConfigLoader();
 * loader.addSource(new FileConfigSource(Paths.get("base.yaml")),
 *                  ConfigPriority.LOW);      // 低优先级——先加载
 * loader.addSource(new FileConfigSource(Paths.get("override.yaml")),
 *                  ConfigPriority.HIGH);     // 高优先级——覆盖低优先级
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
 */
public final class ConfigLoader {

    private static final ConfigLoader SYSTEM = new ConfigLoader();

    private final List<SourceEntry> entries = new CopyOnWriteArrayList<>();

    /** 创建隔离的 ConfigLoader 实例（来源列表独立，不与 {@link #system()} 共享）。 */
    public ConfigLoader() {}

    /** 全局默认 ConfigLoader 实例。 */
    public static ConfigLoader system() {
        return SYSTEM;
    }

    // ====== 来源管理 ======

    /** 以 {@link ConfigPriority#NORMAL} 优先级添加配置来源。 */
    public void addSource(ConfigSource source) {
        addSource(source, ConfigPriority.NORMAL);
    }

    /**
     * 以指定优先级添加配置来源。
     *
     * @param source   配置来源
     * @param priority 优先级（高优先级覆盖低优先级）
     */
    public void addSource(ConfigSource source, ConfigPriority priority) {
        entries.add(new SourceEntry(source, priority));
    }

    /** 移除指定来源。 */
    public boolean removeSource(ConfigSource source) {
        return entries.removeIf(e -> e.source == source);
    }

    /** 返回当前已注册的来源列表（不可变视图）。 */
    public List<ConfigSource> getSources() {
        return entries.stream()
                .map(SourceEntry::source)
                .collect(Collectors.toUnmodifiableList());
    }

    // ====== 单次加载 ======

    /**
     * 加载并合并所有已注册来源的配置。
     * <p>合并顺序：先按优先级从低到高，再按添加顺序（后添加覆盖先添加）。</p>
     *
     * @return 合并后的配置
     * @throws ConfigException 加载失败时抛出
     */
    public ConfigMap load() {
        if (entries.isEmpty()) return ConfigMap.empty();

        List<SourceEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingInt(e -> e.priority.ordinal()));

        ConfigMap merged = ConfigMap.empty();
        for (SourceEntry e : sorted) {
            ConfigMap cfg = e.source.load();
            merged = ConfigMap.merge(merged, cfg);
        }
        return merged;
    }

    // ====== 分阶段加载（Java 编排） ======

    /**
     * 分阶段加载配置。先加载所有已注册来源，然后反复调用回调，
     * 由回调从已加载的配置中读取普通 key 的值来确定下一步加载哪些额外来源，
     * 直到回调返回空列表为止。
     * <p>new 来源默认以 {@link ConfigPriority#NORMAL} 添加。
     * 若需要在 resolve 中使用不同优先级，可调用
     * {@link #addSource(ConfigSource, ConfigPriority)} 显式指定。</p>
     * <p>自动检测循环：同一来源位置不会被重复加载。</p>
     *
     * @param resolver 回调函数，接收当前合并后的配置，返回下一步要加载的来源列表
     * @return 最终合并后的配置
     */
    public ConfigMap resolve(ConfigResolver resolver) {
        ConfigMap merged = load();
        Set<String> seen = new HashSet<>();
        for (SourceEntry e : entries) {
            String loc = e.source.location();
            if (loc != null) seen.add(loc);
        }

        int maxRounds = 100;
        for (int round = 0; round < maxRounds; round++) {
            List<ConfigSource> additional = resolver.resolve(merged);
            if (additional == null || additional.isEmpty()) break;

            List<ConfigSource> newSources = new ArrayList<>();
            for (ConfigSource src : additional) {
                String loc = src.location();
                if (loc != null && !seen.add(loc)) {
                    continue;
                }
                newSources.add(src);
                addSource(src); // 默认 NORMAL 优先级
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

    // ====== 内部 ======

    private record SourceEntry(ConfigSource source, ConfigPriority priority) {}
}
