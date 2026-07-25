package com.flora.cache.store;

import com.flora.cache.BoundedCache;
import com.flora.cache.ObservableCache;

/**
 * 有界缓存抽象基类：在共享引擎 {@link CacheSupport} 之上兑现
 * {@link ObservableCache}（事件）+ {@link BoundedCache}（容量约束 + 可挂策略）。
 * 引擎本身只声明 {@link com.flora.cache.Cache}，两个能力装饰在此处按需在类型层 opt-in；
 * 具体方法体（addListener/setEvictionPolicy 等）由引擎以 concrete 方法提供，本类直接继承复用。
 * <p>
 * 本地有界缓存（如 {@code MemoryCache}）继承本类即可同时获得：存储、事件监听、可挂策略与尺寸约束。
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
