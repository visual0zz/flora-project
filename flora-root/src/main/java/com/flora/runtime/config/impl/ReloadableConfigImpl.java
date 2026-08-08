package com.flora.runtime.config.impl;

import com.flora.runtime.config.interfaces.Config;
import com.flora.runtime.config.interfaces.ReloadableConfig;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link ReloadableConfig} 的可变实现：内部持有当前 {@link Config}（volatile 引用，读线程安全）。
 * <p>{@link #replaceWith(Config)} 全量替换底层配置；{@link #refreshWith(Config)} 按合并语义更新
 * ——新配置的值覆盖旧值，未涉及的旧值保留（类似 {@code putAll} 的深度版）。</p>
 */
public class ReloadableConfigImpl implements ReloadableConfig {

    private volatile Config current;

    /** 以空配置初始化。 */
    public ReloadableConfigImpl() {
        this.current = MapConfig.empty();
    }

    /** 以初始配置初始化。 */
    public ReloadableConfigImpl(Config initial) {
        this.current = initial == null ? MapConfig.empty() : initial;
    }

    @Override
    public void replaceWith(Config newConfig) {
        this.current = newConfig == null ? MapConfig.empty() : newConfig;
    }

    @Override
    public void refreshWith(Config newConfig) {
        if (newConfig == null) return;
        this.current = MapConfig.of(mergeDeep(current.toMapTree(), newConfig.toMapTree()));
    }

    // ====== 转发当前快照 ======

    @Override
    public Object get(String path) {
        return current.get(path);
    }

    @Override
    public Config getSubConfig(String path) {
        return current.getSubConfig(path);
    }

    @Override
    public Map<String, Object> toMapTree() {
        return current.toMapTree();
    }

    @Override
    public Map<String, Object> toLongKeyMap() {
        return current.toLongKeyMap();
    }

    @Override
    public boolean isEmpty() {
        return current.isEmpty();
    }

    /** 深度合并：overlay 的值覆盖 base，两者均为 Map 时递归；其余类型直接覆盖。 */
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
}
