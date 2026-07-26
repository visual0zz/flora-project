package com.flora.cache;

/**
 * 可配置驱逐策略的缓存契约：提供挂载 / 卸除淘汰策略插件（{@link EvictionPolicy}）的能力。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public interface MemoryCache<K, V> extends BoundedCache<K,V> {

    /**
     * 挂载淘汰策略插件；传入 {@code null} 表示移除插件。重复挂载只会替换插件，不会嵌套。
     *
     * @param policy 淘汰策略插件，或 {@code null}
     */
    void setEvictionPolicy(EvictionPolicy<K, V> policy);

    /** 返回当前挂载的淘汰策略插件；未挂载返回 {@code null}。 */
    EvictionPolicy<K, V> evictionPolicy();
}
