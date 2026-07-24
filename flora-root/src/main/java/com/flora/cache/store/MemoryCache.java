package com.flora.cache.store;

import com.flora.cache.eviction.WTinyLfuEvictionPolicy;
import com.flora.tag.WorkInProgress;

/**
 * W-TinyLFU + TTL 缓存。
 * <p>
 * 作为插件组合示例：底层是 {@link ConcurrentHashMapStore}（原始存储），
 * 通过 {@link #setEvictionPolicy} 挂上 {@link WTinyLfuEvictionPolicy}（淘汰策略插件）
 * 即获得容量约束与 W-TinyLFU 淘汰。对外以 {@link com.flora.cache.BoundedCacheStore}
 * 形式服务（存储 + 事件 + 尺寸 + 策略），无需引入额外的组合包装类型。
 * 更换策略（LRU / LFU / FIFO）只需替换插件。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
@WorkInProgress
public final class MemoryCache<K, V> extends ConcurrentHashMapStore<K, V> {

    public MemoryCache() {
        this(-1);
    }

    public MemoryCache(long capacity) {
        super(capacity);
        setEvictionPolicy(new WTinyLfuEvictionPolicy<>(capacity, this::approxCount));
    }
}
