package com.flora.cache.store;

import com.flora.cache.CacheEventType;
import com.flora.cache.CacheEventListener;
import com.flora.cache.EvictionPolicy;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * 缓存引擎：承载读写、TTL、事件派发、可选淘汰策略与可选容量的通用编排。
 * 不持有具体存储，数据访问通过 {@link RawStore} 完成。
 * <p>
 * 行为契约与 {@link com.flora.cache.Cache}/{@link com.flora.cache.ObservableCache}/
 * {@link com.flora.cache.EvictableCache}/{@link com.flora.cache.BoundedCache} 一致，
 * 由组合它的缓存类对外暴露对应接口。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public class CacheEngine<K, V> {

    private final RawStore<K, V> store;

    private volatile EvictionPolicy<K, V> policy;

    private final Map<CacheEventType, List<CacheEventListener<? super K, ? super V>>> listeners
            = new ConcurrentHashMap<>();

    private final long capacity;

    private final AtomicBoolean evicting = new AtomicBoolean();

    public CacheEngine(RawStore<K, V> store) {
        this(store, -1L);
    }

    public CacheEngine(RawStore<K, V> store, long capacity) {
        this.store = Objects.requireNonNull(store, "store");
        this.capacity = capacity;
    }

    // ========== 写入 ==========

    public void put(K key, V value) {
        if (value == null) throw new NullPointerException("value must not be null");
        if (store.rawContains(key)) {
            store.rawPut(key, value);
            onPut(key, true);
            onTouch(key, true);
            fire(CacheEventType.UPDATE, key, () -> store.rawGet(key), () -> value);
            fire(CacheEventType.MUTATE, key, () -> store.rawGet(key), () -> value);
        } else {
            ensureCapacity();
            store.rawPut(key, value);
            onPut(key, false);
            onTouch(key, false);
            fire(CacheEventType.INSERT, key, () -> null, () -> value);
            fire(CacheEventType.MUTATE, key, () -> null, () -> value);
        }
    }

    public boolean putIfAbsent(K key, V value) {
        if (value == null) throw new NullPointerException("value must not be null");
        if (store.rawContains(key)) {
            onPut(key, true);
            onTouch(key, true);
            return false;
        }
        ensureCapacity();
        boolean inserted = store.rawPutIfAbsent(key, value);
        if (inserted) {
            onPut(key, false);
            onTouch(key, false);
            fire(CacheEventType.INSERT, key, () -> null, () -> value);
            fire(CacheEventType.MUTATE, key, () -> null, () -> value);
        } else {
            onPut(key, true);
            onTouch(key, true);
        }
        return inserted;
    }

    public void put(K key, V value, Duration duration) {
        if (value == null) throw new NullPointerException("value must not be null");
        if (duration == null) {
            put(key, value);
            return;
        }
        if (duration.isZero() || duration.isNegative()) {
            expireKey(key);
            return;
        }
        if (store.rawContains(key)) {
            store.rawPut(key, value, duration);
            onPut(key, true);
            onTouch(key, true);
            fire(CacheEventType.UPDATE, key, () -> store.rawGet(key), () -> value);
            fire(CacheEventType.MUTATE, key, () -> store.rawGet(key), () -> value);
        } else {
            ensureCapacity();
            store.rawPut(key, value, duration);
            onPut(key, false);
            onTouch(key, false);
            fire(CacheEventType.INSERT, key, () -> null, () -> value);
            fire(CacheEventType.MUTATE, key, () -> null, () -> value);
        }
    }

    public boolean putIfAbsent(K key, V value, Duration duration) {
        if (value == null) throw new NullPointerException("value must not be null");
        if (duration == null) {
            return putIfAbsent(key, value);
        }
        if (duration.isZero() || duration.isNegative()) {
            return false;
        }
        if (store.rawContains(key)) {
            onPut(key, true);
            onTouch(key, true);
            return false;
        }
        ensureCapacity();
        boolean inserted = store.rawPutIfAbsent(key, value, duration);
        if (inserted) {
            onPut(key, false);
            onTouch(key, false);
            fire(CacheEventType.INSERT, key, () -> null, () -> value);
            fire(CacheEventType.MUTATE, key, () -> null, () -> value);
        } else {
            onPut(key, true);
            onTouch(key, true);
        }
        return inserted;
    }

    // ========== 读取 ==========

    public V get(K key) {
        V v = store.rawGet(key);
        if (v == null) {
            // 惰性过期：发现已过期即走过期删除路径（统一派发 EXPIRE 事件并通知策略）
            if (store.rawIsExpired(key)) expireKey(key);
            onGet(key, false);
            onTouch(key, false);
            return null;
        }
        onGet(key, true);
        onTouch(key, true);
        return v;
    }

    // ========== TTL 管理 ==========

    public void setTtl(K key, Duration duration) {
        if (duration == null) return;
        if (duration.isZero() || duration.isNegative()) {
            expireKey(key); // 刷新成零/负时长 = 立即过期，走过期删除管线（而非显式删除）
            return;
        }
        if (store.rawContains(key)) {
            store.rawSetTtl(key, duration);
            onTouch(key, true); // TTL 刷新 = 重新确认条目仍被需要，刷新其淘汰热度
            // cur 以惰性提供者传入：仅当确有 TOUCH/MUTATE 监听器时才回读存储
            fire(CacheEventType.TOUCH, key, () -> store.rawGet(key), () -> store.rawGet(key));
            fire(CacheEventType.MUTATE, key, () -> store.rawGet(key), () -> store.rawGet(key));
        } else {
            store.rawSetTtl(key, duration);
        }
    }

    public Duration ttl(K key) {
        return store.rawTtl(key);
    }

    // ========== 删除 ==========

    public V remove(K key) {
        V old = store.rawRemove(key);
        if (old == null) return null;
        onRemove(key);
        onExplicitRemove(key);
        fire(CacheEventType.REMOVE, key, () -> old, () -> null);
        fire(CacheEventType.INVALIDATE, key, () -> old, () -> null);
        return old;
    }

    public void clear() {
        store.rawClear();
        EvictionPolicy<K, V> p = policy;
        if (p != null) p.clear();
        fire(CacheEventType.CLEAR, null, () -> null, () -> null);
    }

    // ========== 查询 ==========

    public long approxCount() {
        return store.rawCount();
    }

    public boolean containsKey(K key) {
        return store.rawContains(key);
    }

    // ========== 可选：淘汰策略 ==========

    public void setEvictionPolicy(EvictionPolicy<K, V> policy) {
        this.policy = policy;
    }

    public EvictionPolicy<K, V> evictionPolicy() {
        return policy;
    }

    // ========== 可选：容量约束 ==========

    public long cleanUp() {
        long count = sweepExpired();
        ensureCapacity();
        return count;
    }

    public boolean isFull() {
        return capacity > 0 && approxCount() >= capacity;
    }

    public long capacity() {
        return capacity;
    }

    // ========== 策略回调 ==========

    /** 向已挂载的淘汰策略喂数据。 */

    private void onPut(K key, boolean existed) {
        EvictionPolicy<K, V> p = policy;
        if (p != null) p.onPut(key, existed);
    }

    private void onGet(K key, boolean existed) {
        EvictionPolicy<K, V> p = policy;
        if (p != null) p.onGet(key, existed);
    }

    private void onTouch(K key, boolean existed) {
        EvictionPolicy<K, V> p = policy;
        if (p != null) p.onTouch(key, existed);
    }

    private void onRemove(K key) {
        EvictionPolicy<K, V> p = policy;
        if (p != null) p.onRemove(key);
    }

    private void onExplicitRemove(K key) {
        EvictionPolicy<K, V> p = policy;
        if (p != null) p.onExplicitRemove(key);
    }

    private void onEvict(K key) {
        EvictionPolicy<K, V> p = policy;
        if (p != null) p.onEvict(key);
    }

    private void onExpire(K key) {
        EvictionPolicy<K, V> p = policy;
        if (p != null) p.onExpire(key);
    }

    // ========== 过期扫描 + 淘汰驱动 ==========

    /** 扫描并清理过期项；仅在 cleanUp / ensureCapacity 时低频发生。 */
    private long sweepExpired() {
        long count = 0;
        for (K key : store.rawKeys()) {
            if (store.rawIsExpired(key) && expireKey(key)) count++;
        }
        return count;
    }

    /**
     * 把单个过期 key 走删除管线：从存储移除 + 通知策略（onRemove + onExpire）+ 派发 EXPIRE/INVALIDATE 事件。
     * 返回是否真的删除了一个值（并发已删则返回 {@code false}）。
     * 惰性过期（{@link #get}）与主动扫描（{@link #sweepExpired}）共用此路径，保证删除语义唯一。
     */
    private boolean expireKey(K key) {
        V old = store.rawRemove(key);
        if (old == null) return false;
        onRemove(key);
        onExpire(key);
        fire(CacheEventType.EXPIRE, key, () -> old, () -> null);
        fire(CacheEventType.INVALIDATE, key, () -> old, () -> null);
        return true;
    }

    /**
     * 写入导致容量增长前的钩子：腾出容量、清理过期。
     * {@code capacity <= 0} 时无界，直接返回；否则先扫描过期、再驱动策略淘汰。
     * 容量淘汰时引擎删除存储并派发 EVICT/INVALIDATE 事件；受害者由策略在
     * {@link EvictionPolicy#selectEvictVictim()} 内摘除。
     */
    private void ensureCapacity() {
        if (capacity <= 0) return;
        sweepExpired();
        if (!evicting.compareAndSet(false, true)) return;
        try {
            EvictionPolicy<K, V> p = evictionPolicy();
            K victim;
            while (p != null && (victim = p.selectEvictVictim()) != null) {
                V old = store.rawRemove(victim);
                if (old != null) {
                    onRemove(victim);
                    onEvict(victim);
                    fire(CacheEventType.EVICT, victim, () -> old, () -> null);
                    fire(CacheEventType.INVALIDATE, victim, () -> old, () -> null);
                }
            }
        } finally {
            evicting.set(false);
        }
    }

    // ========== 事件监听器 ==========

    public void addListener(CacheEventType type, CacheEventListener<? super K, ? super V> listener) {
        if (type == null || listener == null) return;
        listeners.computeIfAbsent(type, _ -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public void removeListener(CacheEventType type, CacheEventListener<? super K, ? super V> listener) {
        if (type == null || listener == null) return;
        List<CacheEventListener<? super K, ? super V>> list = listeners.get(type);
        if (list != null) list.remove(listener);
    }

    public void removeListeners(CacheEventType type) {
        if (type == null) return;
        listeners.remove(type);
    }

    private boolean hasListeners(CacheEventType type) {
        List<CacheEventListener<? super K, ? super V>> list = listeners.get(type);
        return list != null && !list.isEmpty();
    }

    /**
     * 触发事件。约定：实际存储操作已完成之后才调用本方法，故监听器异常不影响已提交的业务逻辑。
     * 异常隔离：单个监听器异常被就地吞掉并继续派发其余监听器。
     * <p>
     * {@code oldValue} / {@code newValue} 仅在本类型确有监听器时才求值一次，避免无谓的存储读写。
     */
    private void fire(CacheEventType type, K key,
                      Supplier<? extends V> oldValue, Supplier<? extends V> newValue) {
        List<CacheEventListener<? super K, ? super V>> list = listeners.get(type);
        if (list == null || list.isEmpty()) return;
        for (CacheEventListener<? super K, ? super V> l : list) {
            try {
                l.onEvent(type, key, oldValue, newValue);
            } catch (RuntimeException ignore) {
                // 监听器故障不应影响缓存主流程与同批次其他监听器
            }
        }
    }
}
