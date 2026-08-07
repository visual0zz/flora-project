package com.flora.cache.store;

import com.flora.cache.CacheEventType;
import com.flora.cache.CacheEventListener;
import com.flora.cache.EvictionPolicy;
import com.flora.cache.MemoryCache;
import com.flora.cache.eviction.WTinyLfuEvictionPolicy;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * W-TinyLFU + TTL 的本地内存缓存。
 * <p>
 * 直接承载完整缓存行为（读写、TTL、惰性/主动过期、可选淘汰策略与容量约束），
 * 不依赖任何独立引擎类。过期采用惰性删除（{@code get} 时发现过期即隐藏）+ 主动扫描
 * （{@code cleanUp()}）。{@code capacity <= 0} 时为无界模式：策略不参与淘汰。
 * <p>
 * 本类只实现 {@link MemoryCache} 契约，不承载可观测能力；如需事件监听，请用
 * {@link CacheListenerAdapter#of(MemoryCache)} 包装。
 * <p>
 * 空值契约：{@code key} 与 {@code value} 均不可为 {@code null}，所有公开方法在入口处
 * 显式校验，违反即抛出 {@link NullPointerException}。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public class ConcurrentHashMapCache<K, V>
        implements MemoryCache<K, V> {

    /** 键 → 值 */
    private final ConcurrentHashMap<K, V> map = new ConcurrentHashMap<>();
    /** 键 → 绝对过期时间戳(ms)；不存在表示永不过期 */
    private final ConcurrentHashMap<K, Long> expiry = new ConcurrentHashMap<>();

    private EvictionPolicy<K, V> policy;
    /** 内部移除（逐出/过期）监听；由可观测装饰器注入，用于把 EVICT/EXPIRE 桥接给用户监听器。 */
    private volatile CacheEventListener<K, V> removalListener;
    private final long capacity;
    /** 容量淘汰时的并发闸门，避免 ensureCapacity 重入导致重复淘汰 */
    private final AtomicBoolean evicting = new AtomicBoolean();

    public ConcurrentHashMapCache() {
        this(-1);
    }

    /**
     * @param capacity 容量上限（{@code <=0} 表示无上限，自带 W-TinyLFU 休眠、永不淘汰）
     */
    public ConcurrentHashMapCache(long capacity) {
        this.capacity = capacity;
        setEvictionPolicy(new WTinyLfuEvictionPolicy<>(capacity, this::approxCount));
    }

    /**
     * 使用自定义淘汰策略构造。
     * <p>
     * 容量上限由 {@code capacity} 决定（仅影响 {@link #isFull()}/{@link #capacity()} 与
     * {@link #ensureCapacity()} 的软阈值判断）；策略内部的容量与频率统计需由调用方自行
     * 在其实现中配置。{@code capacity <= 0} 时为无界模式：{@link #ensureCapacity()} 直接返回，
     * 是否淘汰完全交由 {@code policy} 决定。
     *
     * @param capacity 容量上限（{@code <=0} 表示无上限）
     * @param policy   自定义淘汰策略（不可为 {@code null}）
     */
    public ConcurrentHashMapCache(long capacity, EvictionPolicy<K, V> policy) {
        this.capacity = capacity;
        setEvictionPolicy(Objects.requireNonNull(policy, "policy must not be null"));
    }

    // ========== 逻辑存在判断 ==========

    /** 是否存在且未过期（逻辑存在）。 */
    private boolean storeContains(K key) {
        V v = map.get(key);
        if (v == null) return false;
        Long exp = expiry.get(key);
        return exp == null || !(System.currentTimeMillis() >= exp);
    }

    private boolean storeIsExpired(K key) {
        Long exp = expiry.get(key);
        return exp != null && System.currentTimeMillis() >= exp;
    }

    // ========== 写入 ==========

    @Override
    public void put(K key, V value) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");
        upsert(key, value, null);
    }

    @Override
    public void put(K key, V value, Duration duration) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");
        if (duration == null) {
            put(key, value);
            return;
        }
        if (duration.isZero() || duration.isNegative()) {
            expireKey(key); // 零/负时长 = 立即过期，走过期删除管线
            return;
        }
        upsert(key, value, duration);
    }

    /**
     * put / put(K,V,Duration) 的合并实现：区分新建与覆盖写以驱动淘汰策略热度。
     * 仅新增条目会增大容量，故仅在此路径触发 {@link #ensureCapacity()}。
     */
    private void upsert(K key, V value, Duration duration) {
        boolean existed = storeContains(key);
        if (!existed) ensureCapacity();
        // map.put 返回被替换的前值（新建时为 null），作为 onAccess 的 oldValue
        V oldValue = map.put(key, value);
        if (duration == null) {
            expiry.remove(key);
        } else {
            Long exp = null;
            if (!duration.isNegative() && !duration.equals(Duration.MAX)) {
                exp = System.currentTimeMillis() + duration.toMillis();
            }
            if (exp == null) expiry.remove(key);
            else expiry.put(key, exp);
        }
        EvictionPolicy<K, V> p = policy;
        if (p != null) p.onAccess(key, CacheEventType.PUT, existed, oldValue, value);
    }

    @Override
    public boolean putIfAbsent(K key, V value) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");
        return putIfAbsent(key, value, null);
    }

    @Override
    public boolean putIfAbsent(K key, V value, Duration duration) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(value, "value must not be null");
        if (duration != null && (duration.isZero() || duration.isNegative())) return false;
        if (storeContains(key)) {
            // 已存在：原子写未生效，仅当作一次引用刷新热度；旧值即当前值
            EvictionPolicy<K, V> p = policy;
            if (p != null) p.onAccess(key, CacheEventType.PUT, true, map.get(key), value);
            return false;
        }
        ensureCapacity();
        boolean inserted;
        if (duration == null) {
            inserted = map.putIfAbsent(key, value) == null;
        } else {
            Long exp = null;
            if (!duration.isNegative() && !duration.equals(Duration.MAX)) {
                exp = System.currentTimeMillis() + duration.toMillis();
            }
            Long finalExp = exp;
            inserted = map.computeIfAbsent(key, _ -> {
                if (finalExp != null) expiry.put(key, finalExp);
                return value;
            }) == value;
        }
        EvictionPolicy<K, V> p = policy;
        if (p != null) p.onAccess(key, CacheEventType.PUT, !inserted, null, value);
        return inserted;
    }

    // ========== 读取 ==========

    @Override
    public V get(K key) {
        Objects.requireNonNull(key, "key must not be null");
        V v = map.get(key);
        if (v != null) {
            Long exp = expiry.get(key);
            if (exp == null || !(System.currentTimeMillis() >= exp)) {
                EvictionPolicy<K, V> p = policy;
                if (p != null) p.onAccess(key, CacheEventType.GET, true, null, null);
                return v;
            }
        }
        // 惰性过期：访问时发现过期即走删除管线
        if (storeIsExpired(key)) expireKey(key);
        EvictionPolicy<K, V> p = policy;
        if (p != null) p.onAccess(key, CacheEventType.GET, false, null, null);
        return null;
    }

    @Override
    public boolean containsKey(K key) {
        Objects.requireNonNull(key, "key must not be null");
        return storeContains(key);
    }

    // ========== TTL 管理 ==========

    @Override
    public void setTtl(K key, Duration duration) {
        Objects.requireNonNull(key, "key must not be null");
        if (duration == null) return;
        if (duration.isZero() || duration.isNegative()) {
            expireKey(key); // 刷新成零/负时长 = 立即过期，走过期删除管线
            return;
        }
        // Duration.MAX 表示永不过期（移除 TTL）；仅对存活键操作，过期/缺失键静默忽略，避免复活
        if (!storeContains(key)) return;
        if (duration.equals(Duration.MAX)) expiry.remove(key);
        else expiry.put(key, System.currentTimeMillis() + duration.toMillis());
        // TTL 刷新 = 重新确认条目仍被需要，刷新其淘汰热度（SET_TTL 不改写值，newValue 为 null；
        // oldValue 为被刷新的当前值）
        EvictionPolicy<K, V> p = policy;
        if (p != null) p.onAccess(key, CacheEventType.SET_TTL, true, map.get(key), null);
    }

    @Override
    public Duration ttl(K key) {
        Objects.requireNonNull(key, "key must not be null");
        if (!map.containsKey(key)) return Duration.ZERO;          // 不存在
        Long exp = expiry.get(key);
        if (exp == null) return Duration.MAX;                     // 永不过期
        long remaining = exp - System.currentTimeMillis();
        return remaining > 0 ? Duration.ofMillis(remaining) : Duration.ZERO;
    }

    // ========== 删除 ==========

    @Override
    public V remove(K key) {
        Objects.requireNonNull(key, "key must not be null");
        // 不存在或已过期（逻辑删除）：视为不存在，静默无操作
        if (!storeContains(key)) return null;
        V old = map.remove(key);
        expiry.remove(key);
        if (old == null) return null; // 并发已删
        EvictionPolicy<K, V> p = policy;
        if (p != null) p.onRemove(key, old, CacheEventType.REMOVE);
        return old;
    }

    @Override
    public void clear() {
        map.clear();
        expiry.clear();
        EvictionPolicy<K, V> p = policy;
        if (p != null) p.onClear();
    }

    @Override
    public long approxCount() {
        return map.mappingCount();
    }

    // ========== 过期扫描 + 容量淘汰 ==========

    /** 扫描并清理过期项；仅在 {@link #cleanUp()} / {@link #ensureCapacity()} 时低频发生。 */
    private long sweepExpired() {
        long count = 0;
        for (K key : map.keySet()) {
            if (storeIsExpired(key) && expireKey(key)) count++;
        }
        return count;
    }

    /**
     * 把单个过期 key 走删除管线：从存储移除 + 通知策略（onRemove 带 CacheEventType.EXPIRE）。
     * 返回是否真的删除了一个值（并发已删则返回 {@code false}）。
     * 惰性过期（{@link #get}）与主动扫描（{@link #sweepExpired}）共用此路径。
     */
    private boolean expireKey(K key) {
        V old = map.remove(key);
        expiry.remove(key);
        if (old == null) return false;
        EvictionPolicy<K, V> p = policy;
        if (p != null) p.onRemove(key, old, CacheEventType.EXPIRE);
        CacheEventListener<K, V> rl = removalListener;
        if (rl != null) rl.onEvent(CacheEventType.EXPIRE, key, old, null);
        return true;
    }

    /**
     * 写入导致容量增长前的钩子：腾出容量、清理过期。
     * {@code capacity <= 0} 时无界，直接返回；否则先扫描过期、再驱动策略淘汰。
     * 容量淘汰时由本方法删除存储；受害者由策略在
     * {@link EvictionPolicy#selectVictim()} 内摘除。
     */
    private void ensureCapacity() {
        if (capacity <= 0) return;
        sweepExpired();
        if (!evicting.compareAndSet(false, true)) return; // 重入时跳过，避免重复淘汰
        try {
            EvictionPolicy<K, V> p = evictionPolicy();
            K victim;
            while (p != null && (victim = p.selectVictim()) != null) {
                V old = map.remove(victim);
                expiry.remove(victim);
                if (old != null) {
                    EvictionPolicy<K, V> p1 = policy;
                    if (p1 != null) p1.onRemove(victim, old, CacheEventType.EVICT);
                    CacheEventListener<K, V> rl = removalListener;
                    if (rl != null) rl.onEvent(CacheEventType.EVICT, victim, old, null);
                }
            }
        } finally {
            evicting.set(false);
        }
    }

    // ========== 容量约束（BoundedCache） ==========

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

    // ========== 可选：淘汰策略（MemoryCache） ==========

    @Override
    public void setEvictionPolicy(EvictionPolicy<K, V> policy) {
        this.policy = policy;
    }

    @Override
    public EvictionPolicy<K, V> evictionPolicy() {
        return policy;
    }

    @Override
    public void setInternalRemovalListener(CacheEventListener<K, V> listener) {
        this.removalListener = listener;
    }
}
