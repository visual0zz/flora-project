package com.flora.cache.store;

import com.flora.cache.BoundedCacheStore;
import com.flora.cache.CacheEventType;
import com.flora.cache.CacheEventListener;
import com.flora.cache.CacheStore;
import com.flora.cache.EvictionPolicy;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 组合式有界缓存：把任意 {@link CacheStore}（存储）与任意 {@link EvictionPolicy}
 * （淘汰策略）粘合成一个 {@link BoundedCacheStore}。
 * <p>
 * 写 / 读 / 删操作在转发给存储的同时通知策略；容量超限时驱动策略 {@code evict()}
 * 产出待淘汰 key，由本类负责从存储删除并触发 {@code EVICT}/{@code INVALIDATE} 事件。
 * 淘汰始终走策略内部索引（O(1)），不遍历存储，规避 O(n) 扫描。
 * <p>
 * <b>单体兼容</b>：把存储与策略焊死的整体只要实现 {@link BoundedCacheStore} 即天然兼容，
 * 调用方只认 {@link BoundedCacheStore}，不关心内部是否组合。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public class ComposedCacheStore<K, V> implements BoundedCacheStore<K, V> {

    private final CacheStore<K, V> backing;
    private final EvictionPolicy<K, V> policy;
    private final long capacity;
    private final AtomicBoolean evicting = new AtomicBoolean();

    private final Map<CacheEventType, List<CacheEventListener<? super K, ? super V>>> listeners
            = new ConcurrentHashMap<>();

    /**
     * @param backing  存储后端（负责 KV 与 TTL）
     * @param policy   淘汰策略（负责决策，可自由替换）
     * @param capacity 容量上限（{@code <=0} 表示无上限）
     */
    public ComposedCacheStore(CacheStore<K, V> backing, EvictionPolicy<K, V> policy, long capacity) {
        this.backing = backing;
        this.policy = policy;
        this.capacity = capacity;
    }

    // ========== 写入 ==========

    // 注意顺序：原始 MemoryCache 在写入「新条目」前先 ensureCapacity()（先淘汰再加新），
    // 覆盖写（已存在）则不触发淘汰、仅按访问处理。此处严格保持该时序以对齐行为。

    @Override
    public void put(K key, V value) {
        if (backing.containsKey(key) && !backing.isExpired(key)) {
            backing.put(key, value);
            policy.onAccess(key);
            fireEvent(CacheEventType.UPDATE, key, value);
            fireEvent(CacheEventType.MUTATE, key, value);
        } else {
            enforce();
            backing.put(key, value);
            policy.onPut(key);
            fireEvent(CacheEventType.INSERT, key, value);
            fireEvent(CacheEventType.MUTATE, key, value);
        }
    }

    @Override
    public boolean putIfAbsent(K key, V value) {
        if (backing.containsKey(key) && !backing.isExpired(key)) {
            policy.onAccess(key);
            return false;
        }
        enforce();
        boolean inserted = backing.putIfAbsent(key, value);
        if (inserted) {
            policy.onPut(key);
            fireEvent(CacheEventType.INSERT, key, value);
            fireEvent(CacheEventType.MUTATE, key, value);
        } else {
            policy.onAccess(key);
        }
        return inserted;
    }

    @Override
    public void put(K key, V value, Duration duration) {
        if (duration == null) {
            put(key, value);
            return;
        }
        if (duration.isZero() || duration.isNegative()) {
            remove(key);
            return;
        }
        if (backing.containsKey(key) && !backing.isExpired(key)) {
            backing.put(key, value, duration);
            policy.onAccess(key);
            fireEvent(CacheEventType.UPDATE, key, value);
            fireEvent(CacheEventType.MUTATE, key, value);
        } else {
            enforce();
            backing.put(key, value, duration);
            policy.onPut(key);
            fireEvent(CacheEventType.INSERT, key, value);
            fireEvent(CacheEventType.MUTATE, key, value);
        }
    }

    @Override
    public boolean putIfAbsent(K key, V value, Duration duration) {
        if (duration == null) {
            return putIfAbsent(key, value);
        }
        if (duration.isZero() || duration.isNegative()) {
            return false;
        }
        if (backing.containsKey(key) && !backing.isExpired(key)) {
            policy.onAccess(key);
            return false;
        }
        enforce();
        boolean inserted = backing.putIfAbsent(key, value, duration);
        if (inserted) {
            policy.onPut(key);
            fireEvent(CacheEventType.INSERT, key, value);
            fireEvent(CacheEventType.MUTATE, key, value);
        } else {
            policy.onAccess(key);
        }
        return inserted;
    }

    // ========== 读取 ==========

    @Override
    public V get(K key) {
        V v = backing.get(key);
        policy.onAccess(key); // 命中 / 未命中都计入频率
        return v;
    }

    // ========== TTL 管理 ==========

    @Override
    public void setTtl(K key, Duration duration) {
        if (backing.containsKey(key) && !backing.isExpired(key)) {
            backing.setTtl(key, duration);
            fireEvent(CacheEventType.TOUCH, key, backing.get(key));
            fireEvent(CacheEventType.MUTATE, key, backing.get(key));
        } else {
            backing.setTtl(key, duration);
        }
    }

    @Override
    public Duration ttl(K key) {
        return backing.ttl(key);
    }

    // ========== 删除 ==========

    @Override
    public V remove(K key) {
        V old = backing.remove(key);
        if (old == null) return null;
        policy.onRemove(key);
        fireEvent(CacheEventType.REMOVE, key, old);
        fireEvent(CacheEventType.INVALIDATE, key, old);
        return old;
    }

    @Override
    public void clear() {
        backing.clear();
        policy.clear();
    }

    // ========== 查询 ==========

    @Override
    public long approxCount() {
        return backing.approxCount();
    }

    @Override
    public boolean containsKey(K key) {
        return backing.containsKey(key) && !backing.isExpired(key);
    }

    // ========== BoundedCacheStore ==========

    @Override
    public long gc() {
        long count = sweepExpired();
        enforce();
        return count;
    }

    @Override
    public boolean isFull() {
        return capacity > 0 && backing.approxCount() >= capacity;
    }

    @Override
    public long capacity() {
        return capacity;
    }

    // ========== 内部：过期扫描 + 淘汰驱动 ==========

    /** 扫描并清理过期项（O(n)，仅在 gc / enforce 时低频发生）。 */
    private long sweepExpired() {
        long count = 0;
        for (K key : backing.keys()) {
            if (!backing.isExpired(key)) continue;
            V old = backing.remove(key);
            if (old != null) {
                policy.onRemove(key);
                fireEvent(CacheEventType.EXPIRE, key, old);
                fireEvent(CacheEventType.INVALIDATE, key, old);
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
            K victim;
            while ((victim = policy.evict()) != null) {
                V old = backing.remove(victim);
                if (old != null) {
                    policy.onRemove(victim);
                    fireEvent(CacheEventType.EVICT, victim, old);
                    fireEvent(CacheEventType.INVALIDATE, victim, old);
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
        if (list != null) {
            list.remove(listener);
        }
    }

    @Override
    public void removeListeners(CacheEventType type) {
        if (type == null) return;
        listeners.remove(type);
    }

    /**
     * 触发事件。约定：调用方必须已在对应的实际操作（put/remove/setTtl）完成之后
     * 才调用本方法，因此事件内部即便抛异常也不会破坏已提交的业务逻辑。
     * <p>
     * 异常隔离：单个监听器抛出的异常被就地吞掉并继续派发给其余监听器，
     * 既不向上传播影响调用方，也不跳过后续监听器。
     */
    private void fireEvent(CacheEventType type, K key, V value) {
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
}
