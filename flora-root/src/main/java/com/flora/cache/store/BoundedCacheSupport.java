package com.flora.cache.store;

import com.flora.cache.BoundedCache;
import com.flora.cache.ObservableCache;

/**
 * 有界缓存抽象基类：继承 {@link CacheSupport} 并实现 {@link ObservableCache} 与 {@link BoundedCache}，
 * 供本地有界缓存（如 {@code MemoryCache}）复用，统一获得存储、事件监听、可挂策略与容量约束。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public abstract class BoundedCacheSupport<K, V> extends CacheSupport<K, V>
        implements ObservableCache<K, V>, BoundedCache<K, V> {

    protected BoundedCacheSupport() {
        super();
    }

    /**
     * @param capacity 容量上限（{@code <=0} 表示无上限 / 不淘汰）
     */
    protected BoundedCacheSupport(long capacity) {
        super(capacity);
    }
}
