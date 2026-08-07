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

    /**
     * 安装内部移除（逐出 / 过期）监听；仅当存储引擎自行回收条目（EVICT / EXPIRE）时回调，
     * 显式 {@code remove} / {@code clear} 不由此触发（由可观测装饰器的 REMOVE / CLEAR 事件负责）。
     * 传 {@code null} 取消。
     * <p>
     * 默认空实现：仅支持内部移除派发的实现（如
     * {@code com.flora.cache.store.ConcurrentHashMapCache}）需要覆写。可观测装饰器
     * （{@code CacheListenerAdapter}）在包装时注入此监听，把 EVICT / EXPIRE 桥接给用户监听器，
     * 从而在不让存储直接持有事件总线的情况下补齐内部事件的派发。
     *
     * @param listener 内部移除监听，或 {@code null}
     */
    default void setInternalRemovalListener(CacheEventListener<K, V> listener) {}
}
