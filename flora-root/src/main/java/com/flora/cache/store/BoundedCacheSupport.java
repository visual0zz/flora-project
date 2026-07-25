package com.flora.cache.store;

import com.flora.cache.BoundedCache;
import com.flora.cache.CacheEventType;
import com.flora.cache.EvictionPolicy;
import com.flora.cache.ObservableCache;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 有界缓存抽象基类：继承 {@link CacheSupport} 并实现 {@link ObservableCache} 与 {@link BoundedCache}，
 * 供本地有界缓存（如 {@code MemoryCache}）复用，统一获得存储、事件监听、可挂策略与容量约束。
 * <p>
 * 容量相关能力（容量上限、是否已满、回收、写入前腾位驱动）归属本类；{@link CacheSupport}
 * 只提供通用的存储引擎与写入前钩子 {@code ensureCapacity()}（默认空操作）。
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
    public long cleanUp() {
        long count = sweepExpired();
        ensureCapacity();
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

    // ========== EvictableCache：公开挂载/卸载策略（避免非淘汰子类继承该能力） ==========

    @Override
    public void setEvictionPolicy(EvictionPolicy<K, V> policy) {
        setPolicy(policy);
    }

    @Override
    public EvictionPolicy<K, V> evictionPolicy() {
        return policy();
    }

    /**
     * 写入前钩子：清过期 → 驱动策略淘汰。try-lock 串行化，容量为软上限；
     * {@code capacity <= 0} 时直接返回（无界）。
     * <p>
     * 注意：容量淘汰的受害者已由策略在 {@link EvictionPolicy#selectEvictVictim()} 内自行从索引摘除，
     * 引擎只负责真正删除存储 + 派发 EVICT/INVALIDATE 事件，不再回调 {@code onRemove(EVICT)}（避免双重摘除）。
     */
    @Override
    protected void ensureCapacity() {
        if (capacity <= 0) return;
        sweepExpired();
        if (!evicting.compareAndSet(false, true)) return;
        try {
            EvictionPolicy<K, V> p = evictionPolicy();
            K victim;
            while (p != null && (victim = p.selectEvictVictim()) != null) {
                V old = rawRemove(victim);
                if (old != null) {
                    fire(CacheEventType.EVICT, victim, old, null);
                    fire(CacheEventType.INVALIDATE, victim, old, null);
                }
            }
        } finally {
            evicting.set(false);
        }
    }
}
