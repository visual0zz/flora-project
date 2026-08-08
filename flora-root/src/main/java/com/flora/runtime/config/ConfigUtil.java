package com.flora.runtime.config;

import com.flora.common.RemoteKVSource;
import com.flora.runtime.config.impl.LazyConfigView;
import com.flora.runtime.config.impl.MapConfig;
import com.flora.runtime.config.impl.PlaceholderResolver;
import com.flora.runtime.config.impl.ReloadableConfigImpl;
import com.flora.runtime.config.interfaces.Config;
import com.flora.runtime.config.interfaces.ConfigBuilder;
import com.flora.runtime.config.interfaces.ConfigSource;
import com.flora.runtime.config.interfaces.ConfigUpdater;
import com.flora.runtime.config.interfaces.ConfigView;
import com.flora.runtime.config.interfaces.ReloadableConfig;
import com.flora.runtime.config.source.ClasspathConfigSource;
import com.flora.runtime.config.source.EnvConfigSource;
import com.flora.runtime.config.source.FileConfigSource;
import com.flora.runtime.config.source.RemoteConfigSource;
import com.flora.runtime.config.source.StringConfigSource;
import com.flora.runtime.config.source.SystemPropertiesConfigSource;
import com.flora.tag.ModuleEntry;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 配置加载的流式入口。
 *
 * <pre>{@code
 * Config config = ConfigUtil.newConfig()
 *     .loadFromFile(Paths.get("app.yaml"))
 *     .loadFromString("key=val")
 *     .build();
 *
 * // 可热替换
 * ReloadableConfig r = ConfigUtil.newReloadableConfig()
 *     .loadFromFile(Paths.get("app.yaml"))
 *     .buildReloadable();
 *
 * // 更新已有 ReloadableConfig
 * ConfigUtil.replaceConfig(r).loadFromString("port=8080").flush();
 *
 * // 全局配置（单例，替换式更新）
 * ConfigUtil.replaceSystem().loadFromFile(Paths.get("app.yaml")).flush();
 * }</pre>
 *
 * <p>所有入口共享同一套加载/合并实现（{@link ConfigLoadHelper}），
 * 通过<b>编译期</b>接口类型限制终结方法：{@link #newConfig()} / {@link #newReloadableConfig()}
 * 返回 {@link ConfigBuilder}（只能 build/buildReloadable/view），
 * {@link #refreshConfig(ReloadableConfig)}（合并式）/ {@link #replaceConfig(ReloadableConfig)}（替换式）
 * 与 {@link #refreshSystem()} / {@link #replaceSystem()} 返回 {@link ConfigUpdater}（只能 flush/current）。</p>
 */
@ModuleEntry
public final class ConfigUtil {

    /** 全局单例配置（refreshSystem() / replaceSystem() 的操作目标）。 */
    private static final ReloadableConfig SYSTEM = new ReloadableConfigImpl();

    private ConfigUtil() {}

    /** 创建构建型配置链，最终以 {@link Config} 输出。 */
    public static ConfigBuilder newConfig() {
        return new ConfigLoadHelper(null, false);
    }

    /** 创建构建型配置链，最终以新 {@link ReloadableConfig} 输出。 */
    public static ConfigBuilder newReloadableConfig() {
        return new ConfigLoadHelper(null, false);
    }

    /** 创建更新型配置链，flush 时按合并语义更新（新值覆盖旧值，无新值保留旧值）。 */
    public static ConfigUpdater refreshConfig(ReloadableConfig config) {
        return new ConfigLoadHelper(requireConfig(config), true);
    }

    /** 创建更新型配置链，flush 时全量替换其底层配置。 */
    public static ConfigUpdater replaceConfig(ReloadableConfig config) {
        return new ConfigLoadHelper(requireConfig(config), false);
    }

    /** 返回操作全局单例配置的更新型链，flush 时按合并语义更新全局（新值覆盖旧值，无新值保留旧值）。 */
    public static ConfigUpdater refreshSystem() {
        return new ConfigLoadHelper(SYSTEM, true);
    }
    /** 返回操作全局单例配置的更新型链，flush 时全量替换全局配置。 */
    public static ConfigUpdater replaceSystem() {
        return new ConfigLoadHelper(SYSTEM, false);
    }

    private static ReloadableConfig requireConfig(ReloadableConfig config) {
        if (config == null) throw new ConfigException("ReloadableConfig 不能为 null");
        return config;
    }

    /**
     * 配置链加载器（唯一实现）：收集来源与优先级，按优先级从低到高稳定合并。
     * <p>同时实现 {@link ConfigBuilder}（构建型：{@code build/buildReloadable/view}）与
     * {@link ConfigUpdater}（更新型：{@code flush/current}）两个接口——入口通过返回类型在编译期
     * 限定调用方可用的终结方法；本类的运行期检查仅防御强转等绕过编译期的调用。</p>
     */
    public static final class ConfigLoadHelper implements ConfigBuilder, ConfigUpdater {

        private final ReloadableConfig target;   // null = 构建新的；非 null = 更新目标
        private final boolean merge;             // target 非 null：true=refresh(合并) / false=replace(替换)
        private final List<SourceEntry> entries = new ArrayList<>();
        private final List<SubConfigEntry> subConfigs = new ArrayList<>();

        ConfigLoadHelper(ReloadableConfig target, boolean merge) {
            this.target = target;
            this.merge = merge;
        }

        // ====== 来源收集 ======

        public ConfigLoadHelper loadFrom(ConfigSource source) {
            return loadFrom(ConfigPriority.NORMAL, source);
        }

        public ConfigLoadHelper loadFrom(ConfigPriority priority, ConfigSource source) {
            if (source == null) throw new ConfigException("ConfigSource 不能为 null");
            entries.add(new SourceEntry(source, priority));
            return this;
        }

        public ConfigLoadHelper loadFromFile(Path filePath) {
            return loadFrom(ConfigPriority.NORMAL, new FileConfigSource(filePath));
        }

        public ConfigLoadHelper loadFromFile(ConfigPriority priority, Path filePath) {
            return loadFrom(priority, new FileConfigSource(filePath));
        }

        public ConfigLoadHelper loadFromString(String str) {
            return loadFrom(ConfigPriority.NORMAL, new StringConfigSource(str));
        }

        public ConfigLoadHelper loadFromString(ConfigPriority priority, String str) {
            return loadFrom(priority, new StringConfigSource(str));
        }

        public ConfigLoadHelper loadFromRemote(RemoteKVSource kv, ConfigSchema schema) {
            return loadFrom(ConfigPriority.NORMAL, new RemoteConfigSource(kv, schema));
        }

        public ConfigLoadHelper loadFromRemote(ConfigPriority priority, RemoteKVSource kv, ConfigSchema schema) {
            return loadFrom(priority, new RemoteConfigSource(kv, schema));
        }

        public ConfigLoadHelper loadFromClasspath(String resource) {
            return loadFrom(ConfigPriority.NORMAL, new ClasspathConfigSource(resource));
        }

        public ConfigLoadHelper loadFromClasspath(ConfigPriority priority, String resource) {
            return loadFrom(priority, new ClasspathConfigSource(resource));
        }

        /** 从环境变量加载（schema key 原样作为环境变量名，缺失填 null）。 */
        public ConfigLoadHelper loadFromEnv(ConfigSchema schema) {
            return loadFrom(ConfigPriority.NORMAL, new EnvConfigSource(schema));
        }

        public ConfigLoadHelper loadFromEnv(ConfigPriority priority, ConfigSchema schema) {
            return loadFrom(priority, new EnvConfigSource(schema));
        }

        /** 从系统属性加载（schema key 原样作为属性名，缺失填 null）。 */
        public ConfigLoadHelper loadFromSystemProperties(ConfigSchema schema) {
            return loadFrom(ConfigPriority.NORMAL, new SystemPropertiesConfigSource(schema));
        }

        public ConfigLoadHelper loadFromSystemProperties(ConfigPriority priority, ConfigSchema schema) {
            return loadFrom(priority, new SystemPropertiesConfigSource(schema));
        }

        /**
         * 将某个路径下的子配置提升到整体：如存在 {@code com.flora.database.host = 127.0.0.1}，
         * 执行 {@code loadFromSubConfig("com.flora")} 后顶层 {@code database.host} 被其覆盖。
         * 提升在全部来源合并后按优先级从低到高执行（高优先级提升覆盖低优先级提升，同优先级按添加顺序）。
         */
        public ConfigLoadHelper loadFromSubConfig(String path) {
            return loadFromSubConfig(ConfigPriority.NORMAL, path);
        }

        public ConfigLoadHelper loadFromSubConfig(ConfigPriority priority, String path) {
            if (priority == null) throw new ConfigException("ConfigPriority 不能为 null");
            if (path == null || path.isEmpty()) throw new ConfigException("subConfig 路径不能为空");
            subConfigs.add(new SubConfigEntry(priority, path));
            return this;
        }

        // ====== 终端 ======

        /** 构建静态 {@link Config}（仅未绑定目标时可用）。 */
        public Config build() {
            requireUnbound("build()");
            return merged();
        }

        /** 构建新的 {@link ReloadableConfig}（仅未绑定目标时可用）。 */
        public ReloadableConfig buildReloadable() {
            requireUnbound("buildReloadable()");
            return new ReloadableConfigImpl(merged());
        }

        /**
         * 返回当前链已收集来源的<b>只读查询视图</b>：反映本次加载进行到当前点的中间数据
         * （仅含已 {@code loadFrom*} 的来源与子配置提升，不含绑定目标的历史值），
         * 未绑定与已绑定目标的状态下均可用。创建零成本（不合并、不读取来源），
         * 首次 {@link ConfigView#get} 访问时才合并并缓存；
         * 不触发终端语义（不会 flush 到目标，也不影响后续继续 {@code loadFrom*}）。
         */
        public ConfigView view() {
            return new LazyConfigView(this::mergedRaw);
        }

        /** 返回绑定目标的当前快照（仅绑定目标时可用，如 {@code replaceSystem().current()} 读取全局配置）。 */
        public Config current() {
            if (target == null) throw new IllegalStateException("当前链未绑定 ReloadableConfig，没有可读取的快照");
            return target;
        }

        /** 按绑定语义更新目标：merge 为 true 时 refresh（合并），否则 replace（全量替换）。 */
        public void flush() {
            if (target == null) throw new IllegalStateException("当前链未绑定 ReloadableConfig，应使用 build()/buildReloadable()");
            Config c = merged();
            if (merge) target.refreshWith(c);
            else target.replaceWith(c);
        }

        private void requireUnbound(String method) {
            if (target != null) {
                throw new IllegalStateException("当前链已绑定 ReloadableConfig，不能调用 " + method + "，应使用 flush()");
            }
        }

        // ====== 内部合并 ======

        /** 合并当前已收集来源，返回嵌套 Map（低优先级先、高覆盖低，随后子配置提升）。 */
        private Map<String, Object> mergedRaw() {
            List<SourceEntry> sorted = new ArrayList<>(entries);
            sorted.sort(Comparator.comparingInt(e -> e.priority.ordinal()));  // 稳定排序：低优先级先加载，高覆盖低
            Map<String, Object> acc = new LinkedHashMap<>();
            for (SourceEntry e : sorted) {
                acc = mergeDeep(acc, e.source.load().toMapTree());
            }
            List<SubConfigEntry> sortedSubs = new ArrayList<>(subConfigs);
            sortedSubs.sort(Comparator.comparingInt(e -> e.priority.ordinal()));  // 稳定排序：低优先级提升先执行，高覆盖低
            for (SubConfigEntry e : sortedSubs) {
                Object sub = resolve(acc, e.path);
                if (sub instanceof Map) {
                    acc = mergeDeep(acc, (Map<String, Object>) sub);
                }
            }
            return acc;
        }

        private Config merged() {
            Map<String, Object> raw = mergedRaw();
            // build/flush 路径：静态展开占位符（view() 保留原文，由 LazyConfigView 访问时解释）
            return MapConfig.of(PlaceholderResolver.resolveTree(raw, lookupOf(raw)));
        }

        /** 占位符查找上下文：合并结果树 → 环境变量 → 系统属性。 */
        private static Function<String, String> lookupOf(Map<String, Object> tree) {
            return key -> {
                Object v = resolve(tree, key);
                if (v != null) return String.valueOf(v);
                String env = System.getenv(key);
                if (env != null) return env;
                return System.getProperty(key);
            };
        }

        @SuppressWarnings("unchecked")
        private static Object resolve(Map<String, Object> map, String path) {
            Object current = map;
            for (String key : path.split("\\.")) {
                if (!(current instanceof Map)) return null;
                current = ((Map<String, Object>) current).get(key);
                if (current == null) return null;
            }
            return current;
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> mergeDeep(Map<String, Object> base, Map<String, Object> overlay) {
            Map<String, Object> merged = new LinkedHashMap<>(base);
            for (Map.Entry<String, Object> e : overlay.entrySet()) {
                Object overlayValue = e.getValue();
                Object baseValue = merged.get(e.getKey());
                if (baseValue instanceof Map && overlayValue instanceof Map) {
                    merged.put(e.getKey(), mergeDeep((Map<String, Object>) baseValue, (Map<String, Object>) overlayValue));
                } else {
                    merged.put(e.getKey(), overlayValue);
                }
            }
            return merged;
        }

        private record SourceEntry(ConfigSource source, ConfigPriority priority) {}

        private record SubConfigEntry(ConfigPriority priority, String path) {}
    }
}
