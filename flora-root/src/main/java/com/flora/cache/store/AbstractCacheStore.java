package com.flora.cache.store;

import com.flora.cache.CacheEventType;
import com.flora.cache.CacheEventListener;
import com.flora.cache.CacheStore;
import com.flora.cache.EvictionConfigableCacheStore;
import com.flora.cache.EvictionPolicy;
import com.flora.cache.ObservableCacheStore;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 缓存抽象基类（共享引擎）：实现 {@link CacheStore} + {@link ObservableCacheStore}（事件）
 * + {@link EvictionConfigableCacheStore}（可挂策略），把存储、可选策略与事件「粘合」在一起，
 * 供具体存储子类复用。有界的 {@link AbstractBoundedCacheStore} 与远程的
 * {@link AbstractRemoteCache} 都继承本类，从而共用同一套 put/get/remove/fire 引擎。
 * <p>
 * 子类只需实现一组 {@code rawXxx} 原始存储钩子（KV 与 TTL 的真正读写），
 * 本类负责：写/读/删时的策略回调（{@link EvictionPolicy}）、容量超限时的淘汰驱动、
 * 以及事件派发（含监听器异常隔离）。这样存储实现与淘汰策略完全解耦，且
 * {@link EvictionPolicy} 作为插件挂在缓存上，而非与存储平等组合出的新类型。
 * <p>
 * 策略回调在「策略已挂载（{@code policy != null}）」时即生效——无界但挂了策略的缓存同样会
 * 向策略喂数据（仅统计 / 准入，不触发删除，因为 {@link EvictionPolicy#evict()} 在容量未超限时
 * 返回 {@code null}），从而让「可挂策略」成为与「有界」正交的独立能力。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public abstract class AbstractCacheStore<K, V>
        implements CacheStore<K, V>, ObservableCacheStore<K, V>, EvictionConfigableCacheStore<K, V> {

    private final long capacity;
    private volatile EvictionPolicy<K, V> policy;
    private final AtomicBoolean evicting = new AtomicBoolean();

    private final Map<CacheEventType, List<CacheEventListener<? super K, ? super V>>> listeners
            = new ConcurrentHashMap<>();

    protected AbstractCacheStore() {
        this(-1);
    }

    /**
     * @param capacity 容量上限（{@code <=0} 表示无上限 / 不淘汰）
     */
    protected AbstractCacheStore(long capacity) {
        this.capacity = capacity;
    }

    // ========== 淘汰策略插件 ==========

    @Override
    public void setEvictionPolicy(EvictionPolicy<K, V> policy) {
        this.policy = policy;
    }

    @Override
    public EvictionPolicy<K, V> evictionPolicy() {
        return policy;
    }

    // ========== 写入 ==========

    @Override
    public void put(K key, V value) {
        if (value == null) throw new NullPointerException("value must not be null");
        if (rawContains(key)) {
            rawPut(key, value);
            onAccess(key);
            fire(CacheEventType.UPDATE, key, value);
            fire(CacheEventType.MUTATE, key, value);
        } else {
            enforce();
            rawPut(key, value);
            onPut(key);
            fire(CacheEventType.INSERT, key, value);
            fire(CacheEventType.MUTATE, key, value);
        }
    }

    @Override
    public boolean putIfAbsent(K key, V value) {
        if (value == null) throw new NullPointerException("value must not be null");
        if (rawContains(key)) {
            onAccess(key);
            return false;
        }
        enforce();
        boolean inserted = rawPutIfAbsent(key, value);
        if (inserted) {
            onPut(key);
            fire(CacheEventType.INSERT, key, value);
            fire(CacheEventType.MUTATE, key, value);
        } else {
            onAccess(key);
        }
        return inserted;
    }

    @Override
    public void put(K key, V value, Duration duration) {
        if (value == null) throw new NullPointerException("value must not be null");
        if (duration == null) {
            put(key, value);
            return;
        }
        if (duration.isZero() || duration.isNegative()) {
            remove(key);
            return;
        }
        if (rawContains(key)) {
            rawPut(key, value, duration);
            onAccess(key);
            fire(CacheEventType.UPDATE, key, value);
            fire(CacheEventType.MUTATE, key, value);
        } else {
            enforce();
            rawPut(key, value, duration);
            onPut(key);
            fire(CacheEventType.INSERT, key, value);
            fire(CacheEventType.MUTATE, key, value);
        }
    }

    @Override
    public boolean putIfAbsent(K key, V value, Duration duration) {
        if (value == null) throw new NullPointerException("value must not be null");
        if (duration == null) {
            return putIfAbsent(key, value);
        }
        if (duration.isZero() || duration.isNegative()) {
            return false;
        }
        if (rawContains(key)) {
            onAccess(key);
            return false;
        }
        enforce();
        boolean inserted = rawPutIfAbsent(key, value, duration);
        if (inserted) {
            onPut(key);
            fire(CacheEventType.INSERT, key, value);
            fire(CacheEventType.MUTATE, key, value);
        } else {
            onAccess(key);
        }
        return inserted;
    }

    // ========== 读取 ==========

    @Override
    public V get(K key) {
        V v = rawGet(key);
        onAccess(key); // 命中 / 未命中都计入频率
        return v;
    }

    // ========== TTL 管理 ==========

    @Override
    public void setTtl(K key, Duration duration) {
        if (duration == null) return;
        if (duration.isZero() || duration.isNegative()) {
            remove(key); // 刷新成零/负时长等价于删除
            return;
        }
        if (rawContains(key)) {
            rawSetTtl(key, duration);
            fire(CacheEventType.TOUCH, key, rawGet(key));
            fire(CacheEventType.MUTATE, key, rawGet(key));
        } else {
            rawSetTtl(key, duration);
        }
    }

    @Override
    public Duration ttl(K key) {
        return rawTtl(key);
    }

    // ========== 删除 ==========

    @Override
    public V remove(K key) {
        V old = rawRemove(key);
        if (old == null) return null;
        onRemove(key);
        fire(CacheEventType.REMOVE, key, old);
        fire(CacheEventType.INVALIDATE, key, old);
        return old;
    }

    @Override
    public void clear() {
        rawClear();
        EvictionPolicy<K, V> p = policy;
        if (p != null) p.clear();
    }

    // ========== 查询 ==========

    @Override
    public long approxCount() {
        return rawCount();
    }

    @Override
    public boolean containsKey(K key) {
        return rawContains(key);
    }

    @Override
    public Iterable<K> keys() {
        return rawKeys();
    }

    @Override
    public boolean isExpired(K key) {
        return rawIsExpired(key);
    }

    // ========== 容量与回收（供有界子类 AbstractBoundedCacheStore 继承以兑现 BoundedCacheStore） ==========

    public long gc() {
        long count = sweepExpired();
        enforce();
        return count;
    }

    public boolean isFull() {
        return capacity > 0 && rawCount() >= capacity;
    }

    public long capacity() {
        return capacity;
    }

    // ========== 内部：策略回调 ==========

    // 唤醒闸门：策略已挂载即生效（不再要求 capacity > 0）。无界但挂了策略的缓存同样喂数据，
    // 但 evict() 内部会因容量未超限返回 null，从而只统计不删除——使「可挂策略」与「有界」正交。
    private void onPut(K key) {
        EvictionPolicy<K, V> p = policy;
        if (p != null) p.onPut(key);
    }

    private void onAccess(K key) {
        EvictionPolicy<K, V> p = policy;
        if (p != null) p.onAccess(key);
    }

    private void onRemove(K key) {
        EvictionPolicy<K, V> p = policy;
        if (p != null) p.onRemove(key);
    }

    // ========== 内部：过期扫描 + 淘汰驱动 ==========

    /** 扫描并清理过期项（O(n)，仅在 gc / enforce 时低频发生）。 */
    private long sweepExpired() {
        long count = 0;
        for (K key : rawKeys()) {
            if (!rawIsExpired(key)) continue;
            V old = rawRemove(key);
            if (old != null) {
                onRemove(key);
                fire(CacheEventType.EXPIRE, key, old);
                fire(CacheEventType.INVALIDATE, key, old);
                count++;
            }
        }
        return count;
    }

    /** 确保容量：清过期 → 驱动策略淘汰。try-lock 串行化，容量为软上限。 */
    private void enforce() {
        if (capacity <= 0) return;
        sweepExpired();
        if (!evicting.compareAndSet(false, true)) return;
        try {
            EvictionPolicy<K, V> p = policy;
            K victim;
            while (p != null && (victim = p.evict()) != null) {
                V old = rawRemove(victim);
                if (old != null) {
                    onRemove(victim);
                    fire(CacheEventType.EVICT, victim, old);
                    fire(CacheEventType.INVALIDATE, victim, old);
                }
            }
        } finally {
            evicting.set(false);
        }
    }

    // ========== 事件监听器 ==========

    @Override
    public void addListener(CacheEventType type, CacheEventListener<? super K, ? super V> listener) {
        if (type == null || listener == null) return;
        listeners.computeIfAbsent(type, _ -> new CopyOnWriteArrayList<>()).add(listener);
    }

    @Override
    public void removeListener(CacheEventType type, CacheEventListener<? super K, ? super V> listener) {
        if (type == null || listener == null) return;
        List<CacheEventListener<? super K, ? super V>> list = listeners.get(type);
        if (list != null) list.remove(listener);
    }

    @Override
    public void removeListeners(CacheEventType type) {
        if (type == null) return;
        listeners.remove(type);
    }

    /**
     * 触发事件。约定：实际存储操作已完成之后才调用本方法，故监听器异常不影响已提交的业务逻辑。
     * 异常隔离：单个监听器异常被就地吞掉并继续派发其余监听器。
     */
    private void fire(CacheEventType type, K key, V value) {
        List<CacheEventListener<? super K, ? super V>> list = listeners.get(type);
        if (list == null) return;
        for (CacheEventListener<? super K, ? super V> l : list) {
            try {
                l.onEvent(type, key, value);
            } catch (RuntimeException ignore) {
                // 监听器故障不应影响缓存主流程与同批次其他监听器
            }
        }
    }

    // ========== 原始存储钩子（子类实现） ==========

    /** 覆盖写入（永不过期）。 */
    protected abstract void rawPut(K key, V value);

    /** 覆盖写入（带 TTL，duration 已保证为正数）。 */
    protected abstract void rawPut(K key, V value, Duration duration);

    /** 原子写入，返回是否写入成功（仅当 key 不存在）。 */
    protected abstract boolean rawPutIfAbsent(K key, V value);

    /** 原子写入（带 TTL，duration 已保证为正数），返回是否写入成功。 */
    protected abstract boolean rawPutIfAbsent(K key, V value, Duration duration);

    /** 读取值；不存在返回 {@code null}。 */
    protected abstract V rawGet(K key);

    /** 删除并返回旧值；不存在返回 {@code null}。 */
    protected abstract V rawRemove(K key);

    /** 是否存在且未过期（用于写时分支判断与 {@link #containsKey}）。 */
    protected abstract boolean rawContains(K key);

    /** 剩余过期时长；不存在返回 {@code null}，永不过期返回 {@link Duration#ZERO}。 */
    protected abstract Duration rawTtl(K key);

    /** 设置/更新过期时间（key 不存在由子类决定行为）。 */
    protected abstract void rawSetTtl(K key, Duration duration);

    /** 清空全部。 */
    protected abstract void rawClear();

    /** 所有 key 的快照（供 gc 扫描）。 */
    protected abstract Iterable<K> rawKeys();

    /** 指定 key 是否已过期（未过期或不存在返回 {@code false}）。 */
    protected abstract boolean rawIsExpired(K key);

    /** 当前条目数量近似值。 */
    protected abstract long rawCount();
}
