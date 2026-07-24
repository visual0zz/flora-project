package com.flora.cache.store;

import com.flora.cache.CacheStore;
import com.flora.cache.eviction.WTinyLfuEvictionPolicy;
import com.flora.tag.WorkInProgress;

/**
 * W-TinyLFU + TTL 缓存。
 * <p>
 * 作为组合式缓存示例：内部由 {@link ConcurrentHashMapStore}（存储）与
 * {@link WTinyLfuEvictionPolicy}（淘汰策略）组装而成，本身仍实现
 * {@link com.flora.cache.BoundedCacheStore}，对外 API 与旧版一致。
 * 若需更换淘汰策略（如 LRU / LFU / FIFO），只需替换第二个构造参数。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
@WorkInProgress
public final class MemoryCache<K, V> extends ComposedCacheStore<K, V> {

    public MemoryCache() {
        this(-1);
    }

    public MemoryCache(long capacity) {
        this(new ConcurrentHashMapStore<>(), capacity);
    }

    private MemoryCache(CacheStore<K, V> store, long capacity) {
        super(store,
                new WTinyLfuEvictionPolicy<>(capacity, store::approxCount),
                capacity);
    }
}
