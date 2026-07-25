package com.flora.cache.store;

import com.flora.cache.BoundedCache;
import com.flora.cache.Cache;
import com.flora.cache.CacheEventType;
import com.flora.cache.CacheEventListener;
import com.flora.cache.EvictableCache;
import com.flora.cache.EvictionPolicy;
import com.flora.cache.ObservableCache;
import com.flora.cache.RemovalCause;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 缓存引擎：组合式实现，承载 {@link Cache}/{@link ObservableCache}/{@link EvictableCache}/
 * {@link BoundedCache} 的全部行为（通用读写、TTL、事件派发、可选淘汰策略、可选容量约束），
 * 但不持有具体存储——存储通过 {@link RawStore} 注入。
 * <p>
 * 各场景的 Support 类<b>组合</b>本类（而非继承），从而打破原本 {@code CacheSupport} 父子类强塞
 * 多套职责的耦合：远程缓存组合一个 {@code capacity<=0}、{@code policy=null} 的引擎，不再被动继承
 * 淘汰字段与逻辑；本地缓存组合带容量、可挂策略的引擎。引擎编排逻辑只有一份，遵守 DRY。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public class CacheEngine<K, V> implements Cache<K, V>, ObservableCache<K, V>, EvictableCache<K, V>, BoundedCache<K, V> {

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

    @Override
    public void put(K key, V value) {
        if (value == null) throw new NullPointerException("value must not be null");
        if (store.rawContains(key)) {
            V old = store.rawGet(key);
            store.rawPut(key, value);
            onPut(key, true);
            onTouch(key, true);
            fire(CacheEventType.UPDATE, key, old, value);
            fire(CacheEventType.MUTATE, key, old, value);
        } else {
            ensureCapacity();
            store.rawPut(key, value);
            onPut(key, false);
            onTouch(key, false);
            fire(CacheEventType.INSERT, key, null, value);
            fire(CacheEventType.MUTATE, key, null, value);
        }
    }

    @Override
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
            fire(CacheEventType.INSERT, key, null, value);
            fire(CacheEventType.MUTATE, key, null, value);
        } else {
            onPut(key, true);
            onTouch(key, true);
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
            expireKey(key);
            return;
        }
        if (store.rawContains(key)) {
            V old = store.rawGet(key);
            store.rawPut(key, value, duration);
            onPut(key, true);
            onTouch(key, true);
            fire(CacheEventType.UPDATE, key, old, value);
            fire(CacheEventType.MUTATE, key, old, value);
        } else {
            ensureCapacity();
            store.rawPut(key, value, duration);
            onPut(key, false);
            onTouch(key, false);
            fire(CacheEventType.INSERT, key, null, value);
            fire(CacheEventType.MUTATE, key, null, value);
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
            fire(CacheEventType.INSERT, key, null, value);
            fire(CacheEventType.MUTATE, key, null, value);
        } else {
            onPut(key, true);
            onTouch(key, true);
        }
        return inserted;
    }

    // ========== 读取 ==========

    @Override
    public V get(K key) {
        V v = store.rawGet(key);
        if (v == null) {
            // 惰性过期：rawGet 已隐藏过期值（但不删除），此处把过期删除收归引擎管线，
            // 统一派发 EXPIRE 事件并通知策略，避免存储层私自删除导致策略索引残留幽灵条目。
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

    @Override
    public void setTtl(K key, Duration duration) {
        if (duration == null) return;
        if (duration.isZero() || duration.isNegative()) {
            expireKey(key); // 刷新成零/负时长 = 立即过期，走过期删除管线（而非显式删除）
            return;
        }
        if (store.rawContains(key)) {
            store.rawSetTtl(key, duration);
            onTouch(key, true); // TTL 刷新 = 重新确认条目仍被需要，刷新其淘汰热度
            V cur = store.rawGet(key);
            fire(CacheEventType.TOUCH, key, cur, cur);
            fire(CacheEventType.MUTATE, key, cur, cur);
        } else {
            store.rawSetTtl(key, duration);
        }
    }

    @Override
    public Duration ttl(K key) {
        return store.rawTtl(key);
    }

    // ========== 删除 ==========

    @Override
    public V remove(K key) {
        V old = store.rawRemove(key);
        if (old == null) return null;
        onRemove(key, RemovalCause.EXPLICIT);
        fire(CacheEventType.REMOVE, key, old, null);
        fire(CacheEventType.INVALIDATE, key, old, null);
        return old;
    }

    @Override
    public void clear() {
        store.rawClear();
        EvictionPolicy<K, V> p = policy;
        if (p != null) p.clear();
        fire(CacheEventType.CLEAR, null, null, null);
    }

    // ========== 查询 ==========

    @Override
    public long approxCount() {
        return store.rawCount();
    }

    @Override
    public boolean containsKey(K key) {
        return store.rawContains(key);
    }

    // ========== EvictableCache ==========

    @Override
    public void setEvictionPolicy(EvictionPolicy<K, V> policy) {
        this.policy = policy;
    }

    @Override
    public EvictionPolicy<K, V> evictionPolicy() {
        return policy;
    }

    // ========== BoundedCache ==========

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

    // ========== 内部：策略回调 ==========

    // 策略已挂载（policy != null）时即向策略喂数据；selectEvictVictim() 在容量未超限时返回 null，故不会触发删除。
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

    private void onRemove(K key, RemovalCause cause) {
        EvictionPolicy<K, V> p = policy;
        if (p != null) p.onRemove(key, cause);
    }

    // ========== 内部：过期扫描 + 淘汰驱动 ==========

    /** 扫描并清理过期项（O(n)，仅在 cleanUp / ensureCapacity 时低频发生）。 */
    private long sweepExpired() {
        long count = 0;
        for (K key : store.rawKeys()) {
            if (store.rawIsExpired(key) && expireKey(key)) count++;
        }
        return count;
    }

    /**
     * 把单个过期 key 走引擎删除管线：从存储移除 + 通知策略(EXPIRE) + 派发 EXPIRE/INVALIDATE 事件。
     * 返回是否真的删除了一个值（并发已删则返回 {@code false}）。
     * 惰性过期（{@link #get}）与主动扫描（{@link #sweepExpired}）共用此路径，保证删除语义唯一。
     */
    private boolean expireKey(K key) {
        V old = store.rawRemove(key);
        if (old == null) return false;
        onRemove(key, RemovalCause.EXPIRE);
        fire(CacheEventType.EXPIRE, key, old, null);
        fire(CacheEventType.INVALIDATE, key, old, null);
        return true;
    }

    /**
     * 条目集合即将增长前的钩子（写路径在插入新 key 之前调用），用于腾出容量、清理过期。
     * {@code capacity <= 0} 时无界，直接返回；否则先扫描过期、再驱动策略淘汰。
     * <p>
     * 命名强调「在会导致容量增长的写入之前」调用，而非「写入之后」，以明确其职责是提前腾位。
     * <p>
     * 注意：容量淘汰的受害者已由策略在 {@link EvictionPolicy#selectEvictVictim()} 内自行从索引摘除，
     * 引擎只负责真正删除存储 + 派发 EVICT/INVALIDATE 事件，不再回调 {@code onRemove(EVICT)}（避免双重摘除）。
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
                    fire(CacheEventType.EVICT, victim, old, null);
                    fire(CacheEventType.INVALIDATE, victim, old, null);
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
    private void fire(CacheEventType type, K key, V oldValue, V newValue) {
        List<CacheEventListener<? super K, ? super V>> list = listeners.get(type);
        if (list == null) return;
        for (CacheEventListener<? super K, ? super V> l : list) {
            try {
                l.onEvent(type, key, oldValue, newValue);
            } catch (RuntimeException ignore) {
                // 监听器故障不应影响缓存主流程与同批次其他监听器
            }
        }
    }
}
