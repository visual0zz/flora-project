package com.flora.cache.store;

import com.flora.cache.BoundedCacheStore;

/**
 * 有界缓存抽象基类：在共享引擎 {@link AbstractCacheStore} 之上兑现 {@link BoundedCacheStore}
 * （容量约束 + 淘汰策略）。通过继承引擎复用全部 put/get/remove/fire 逻辑，仅负责把
 * 「容量」这一维度显式声明为有界契约。
 * <p>
 * 本地有界缓存（如 {@code MemoryCache}）继承本类即可同时获得：存储、事件监听
 * （来自 {@link AbstractCacheStore} 实现的 {@link com.flora.cache.ObservableCacheStore}）、
 * 可挂策略（{@link com.flora.cache.EvictionConfigableCacheStore}）与尺寸约束（本类）。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public abstract class AbstractBoundedCacheStore<K, V> extends AbstractCacheStore<K, V>
        implements BoundedCacheStore<K, V> {

    protected AbstractBoundedCacheStore() {
        super();
    }

    /**
     * @param capacity 容量上限（{@code <=0} 表示无上限 / 不淘汰）
     */
    protected AbstractBoundedCacheStore(long capacity) {
        super(capacity);
    }
}
