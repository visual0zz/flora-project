package com.flora.root.runtime.config.impl;

import com.flora.root.runtime.config.interfaces.Config;
import com.flora.root.runtime.config.interfaces.ReloadableConfig;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link ReloadableConfig} 的可变实现：内部以 {@link AtomicReference} 持有当前 {@link Config} 快照，
 * 读侧每次获取的是原子、完整的快照。
 * <p>{@link #replaceWith(Config)} 单次原子替换；{@link #refreshWith(Config)} 按合并语义更新
 * （新值覆盖旧值、无新值保留旧值），采用 CAS 循环——并发刷新时后到的线程基于最新快照重新合并，
 * 不丢失更新。无锁，读侧不阻塞。</p>
 */
public class ReloadableConfigImpl implements ReloadableConfig {

    private final AtomicReference<Config> current;

    /** 以空配置初始化。 */
    public ReloadableConfigImpl() {
        this.current = new AtomicReference<>(MapConfig.empty());
    }

    /** 以初始配置初始化。 */
    public ReloadableConfigImpl(Config initial) {
        this.current = new AtomicReference<>(initial == null ? MapConfig.empty() : initial);
    }

    @Override
    public void replaceWith(Config newConfig) {
        current.set(newConfig == null ? MapConfig.empty() : newConfig);
    }

    @Override
    public void refreshWith(Config newConfig) {
        if (newConfig == null) return;
        while (true) {
            Config snapshot = current.get();
            Config candidate = MapConfig.of(mergeDeep(snapshot.toMapTree(), newConfig.toMapTree()));
            if (current.compareAndSet(snapshot, candidate)) break;
        }
    }

    // ====== 转发当前快照 ======

    @Override
    public Object get(String path) {
        return current.get().get(path);
    }

    @Override
    public Map<String, Object> toMapTree() {
        return current.get().toMapTree();
    }

    @Override
    public Map<String, Object> toLongKeyMap() {
        return current.get().toLongKeyMap();
    }

    @Override
    public boolean isEmpty() {
        return current.get().isEmpty();
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
