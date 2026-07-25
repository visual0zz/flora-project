package com.flora.cache.store;

import com.flora.cache.BoundedCacheStore;
import com.flora.cache.ObservableCacheStore;

/**
 * 有界缓存抽象基类：在共享引擎 {@link AbstractCacheStore} 之上兑现
 * {@link ObservableCacheStore}（事件）+ {@link BoundedCacheStore}（容量约束 + 可挂策略）。
 * 引擎本身只声明 {@link com.flora.cache.CacheStore}，两个能力装饰在此处按需在类型层 opt-in；
 * 具体方法体（addListener/setEvictionPolicy 等）由引擎以 concrete 方法提供，本类直接继承复用。
 * <p>
 * 本地有界缓存（如 {@code MemoryCache}）继承本类即可同时获得：存储、事件监听、可挂策略与尺寸约束。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public abstract class AbstractBoundedCacheStore<K, V> extends AbstractCacheStore<K, V>
        implements ObservableCacheStore<K, V>, BoundedCacheStore<K, V> {

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
