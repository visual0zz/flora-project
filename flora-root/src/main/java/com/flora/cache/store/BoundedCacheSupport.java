package com.flora.cache.store;

import com.flora.cache.BoundedCache;
import com.flora.cache.CacheEventType;
import com.flora.cache.EvictionPolicy;
import com.flora.cache.ObservableCache;
import com.flora.cache.RemovalCause;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 有界缓存抽象基类：继承 {@link CacheSupport} 并实现 {@link ObservableCache} 与 {@link BoundedCache}，
 * 供本地有界缓存（如 {@code MemoryCache}）复用，统一获得存储、事件监听、可挂策略与容量约束。
 * <p>
 * 容量相关能力（容量上限、是否已满、回收、写入后淘汰驱动）归属本类；{@link CacheSupport}
 * 只提供通用的存储引擎与写入后钩子 {@code afterWrite()}（默认空操作）。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public abstract class BoundedCacheSupport<K, V> extends CacheSupport<K, V>
        implements ObservableCache<K, V>, BoundedCache<K, V> {

    private final long capacity;
    private final AtomicBoolean evicting = new AtomicBoolean();

    protected BoundedCacheSupport() {
        super();
        this.capacity = -1;
    }

    /**
     * @param capacity 容量上限（{@code <=0} 表示无上限 / 不淘汰）
     */
    protected BoundedCacheSupport(long capacity) {
        super();
        this.capacity = capacity;
    }

    // ========== 容量与回收（兑现 BoundedCache） ==========

    @Override
    public long gc() {
        long count = sweepExpired();
        afterWrite();
        return count;
    }

    @Override
    public boolean isFull() {
        return capacity > 0 && approxCount() >= capacity;
    }

    @Override
    public long capacity() {
        return capacity;
    }

    /**
     * 写入后钩子：清过期 → 驱动策略淘汰。try-lock 串行化，容量为软上限；
     * {@code capacity <= 0} 时直接返回（无界）。
     */
    @Override
    protected void afterWrite() {
        if (capacity <= 0) return;
        sweepExpired();
        if (!evicting.compareAndSet(false, true)) return;
        try {
            EvictionPolicy<K, V> p = evictionPolicy();
            K victim;
            while (p != null && (victim = p.selectEvictVictim()) != null) {
                V old = rawRemove(victim);
                if (old != null) {
                    onRemove(victim, RemovalCause.EVICT);
                    fire(CacheEventType.EVICT, victim, old, null);
                    fire(CacheEventType.INVALIDATE, victim, old, null);
                }
            }
        } finally {
            evicting.set(false);
        }
    }
}
