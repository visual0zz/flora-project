package com.flora.cache.store;

import com.flora.cache.BoundedCache;
import com.flora.cache.Cache;
import com.flora.cache.CacheEventType;
import com.flora.cache.CacheEventListener;
import com.flora.cache.EvictableCache;
import com.flora.cache.EvictionPolicy;
import com.flora.cache.ObservableCache;
import com.flora.cache.eviction.WTinyLfuEvictionPolicy;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * W-TinyLFU + TTL 的本地内存缓存。
 * <p>
 * 直接承载完整缓存行为（读写、TTL、惰性/主动过期、可选淘汰策略与容量约束、事件派发），
 * 不依赖任何独立引擎类。过期采用惰性删除（{@code get} 时发现过期即隐藏）+ 主动扫描
 * （{@code cleanUp()} 触发 {@code EXPIRE} 事件）。{@code capacity <= 0} 时为无界模式：策略不参与淘汰。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public class MemoryCache<K, V>
        implements Cache<K, V>, ObservableCache<K, V>, EvictableCache<K, V>, BoundedCache<K, V> {

    /** 键 → 值 */
    private final ConcurrentHashMap<K, V> map = new ConcurrentHashMap<>();
    /** 键 → 绝对过期时间戳(ms)；不存在表示永不过期 */
    private final ConcurrentHashMap<K, Long> expiry = new ConcurrentHashMap<>();

    private final Map<CacheEventType, List<CacheEventListener<? super K, ? super V>>> listeners
            = new ConcurrentHashMap<>();

    private EvictionPolicy<K, V> policy;
    private final long capacity;
    /** 容量淘汰时的并发闸门，避免 ensureCapacity 重入导致重复淘汰 */
    private final AtomicBoolean evicting = new AtomicBoolean();

    public MemoryCache() {
        this(-1);
    }

    /**
     * @param capacity 容量上限（{@code <=0} 表示无上限，自带 W-TinyLFU 休眠、永不淘汰）
     */
    public MemoryCache(long capacity) {
        this.capacity = capacity;
        setEvictionPolicy(new WTinyLfuEvictionPolicy<>(capacity, this::approxCount));
    }

    // ========== 时间戳与过期计算 ==========

    private static long now() {
        return System.currentTimeMillis();
    }

    /** 时间戳是否已过（{@code now >= exp}）。 */
    private static boolean expired(long exp) {
        return now() >= exp;
    }

    /**
     * 由 TTL 计算绝对过期时间戳；{@code null}/负/{@link Duration#MAX} 表示永不过期，返回 {@code null}。
     * 正数（含 {@link Duration#ZERO}）返回 {@code now + 毫秒}，即立即过期。
     */
    private static Long computeExpiry(Duration d) {
        if (d == null || d.isNegative() || d.equals(Duration.MAX)) return null;
        return now() + d.toMillis();
    }

    // ========== 存储原语（私有，直接操作 map/expiry） ==========

    private void storePut(K key, V value) {
        map.put(key, value);
        expiry.remove(key);
    }

    private void storePut(K key, V value, Duration duration) {
        map.put(key, value);
        Long exp = computeExpiry(duration);
        if (exp == null) expiry.remove(key);
        else expiry.put(key, exp);
    }

    private boolean storePutIfAbsent(K key, V value) {
        return map.putIfAbsent(key, value) == null;
    }

    private boolean storePutIfAbsent(K key, V value, Duration duration) {
        Long exp = computeExpiry(duration);
        return map.computeIfAbsent(key, _ -> {
            if (exp != null) expiry.put(key, exp);
            return value;
        }) == value;
    }

    /**
     * 读取并惰性隐藏过期值：命中但已过期时返回 {@code null}（真正删除由 {@link #expireKey} 负责，
     * 以保证策略索引与 EXPIRE 事件一致）。
     */
    private V storeGet(K key) {
        V v = map.get(key);
        if (v == null) return null;
        Long exp = expiry.get(key);
        if (exp != null && expired(exp)) return null;
        return v;
    }

    private V storeRemove(K key) {
        V old = map.remove(key);
        expiry.remove(key);
        return old;
    }

    /** 是否存在且未过期（逻辑存在）。 */
    private boolean storeContains(K key) {
        V v = map.get(key);
        if (v == null) return false;
        Long exp = expiry.get(key);
        return exp == null || !expired(exp);
    }

    private Duration storeTtl(K key) {
        if (!map.containsKey(key)) return Duration.ZERO;          // 不存在
        Long exp = expiry.get(key);
        if (exp == null) return Duration.MAX;                     // 永不过期
        long remaining = exp - now();
        return remaining > 0 ? Duration.ofMillis(remaining) : Duration.ZERO; // 已过期 → ZERO
    }

    /** 仅对存活键设置 TTL；{@link Duration#MAX} 表示移除 TTL（永不过期）。调用方须保证 key 存活。 */
    private void storeSetTtl(K key, Duration duration) {
        if (duration.equals(Duration.MAX)) expiry.remove(key);
        else expiry.put(key, now() + duration.toMillis());
    }

    private void storeClear() {
        map.clear();
        expiry.clear();
    }

    private boolean storeIsExpired(K key) {
        Long exp = expiry.get(key);
        return exp != null && expired(exp);
    }

    private long storeCount() {
        return map.mappingCount();
    }

    // ========== 写入 ==========

    @Override
    public void put(K key, V value) {
        Objects.requireNonNull(value, "value must not be null");
        upsert(key, value, null);
    }

    @Override
    public void put(K key, V value, Duration duration) {
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
     * put / put(K,V,Duration) 的合并实现：区分新建与覆盖写，保证 UPDATE/MUTATE 事件携带覆盖前的真实 oldValue。
     * 仅新增条目会增大容量，故仅在此路径触发 {@link #ensureCapacity()}。
     */
    private void upsert(K key, V value, Duration duration) {
        boolean existed = storeContains(key);
        V old = null;
        // 覆盖写时，派发前、覆盖后取值会导致 oldValue 变成新值；故先取值，且无监听器时不读取。
        if (existed && (hasListeners(CacheEventType.UPDATE) || hasListeners(CacheEventType.MUTATE))) {
            old = storeGet(key);
        }
        if (!existed) ensureCapacity();
        if (duration == null) storePut(key, value);
        else storePut(key, value, duration);
        onPut(key, existed);
        onTouch(key, existed);
        fireUpsert(key, old, value, existed);
    }

    @Override
    public boolean putIfAbsent(K key, V value) {
        Objects.requireNonNull(value, "value must not be null");
        return putIfAbsent(key, value, null);
    }

    @Override
    public boolean putIfAbsent(K key, V value, Duration duration) {
        Objects.requireNonNull(value, "value must not be null");
        if (duration != null && (duration.isZero() || duration.isNegative())) return false;
        if (storeContains(key)) {
            // 已存在：原子写未生效，仅当作一次引用刷新热度
            onPut(key, true);
            onTouch(key, true);
            return false;
        }
        ensureCapacity();
        boolean inserted = (duration == null)
                ? storePutIfAbsent(key, value)
                : storePutIfAbsent(key, value, duration);
        if (inserted) {
            onPut(key, false);
            onTouch(key, false);
            fireUpsert(key, null, value, false);
        } else {
            // 并发下被其它线程抢先写入
            onPut(key, true);
            onTouch(key, true);
        }
        return inserted;
    }

    // ========== 读取 ==========

    @Override
    public V get(K key) {
        V v = storeGet(key);
        if (v == null) {
            // 惰性过期：访问时发现过期即走删除管线（统一派发 EXPIRE 事件并通知策略）
            if (storeIsExpired(key)) expireKey(key);
            onGet(key, false);
            onTouch(key, false);
            return null;
        }
        onGet(key, true);
        onTouch(key, true);
        return v;
    }

    @Override
    public boolean containsKey(K key) {
        return storeContains(key);
    }

    // ========== TTL 管理 ==========

    @Override
    public void setTtl(K key, Duration duration) {
        if (duration == null) return;
        if (duration.isZero() || duration.isNegative()) {
            expireKey(key); // 刷新成零/负时长 = 立即过期，走过期删除管线
            return;
        }
        // Duration.MAX 表示永不过期（移除 TTL）；仅对存活键操作，过期/缺失键静默忽略，避免复活
        if (!storeContains(key)) return;
        storeSetTtl(key, duration);
        onTouch(key, true); // TTL 刷新 = 重新确认条目仍被需要，刷新其淘汰热度
        if (hasListeners(CacheEventType.TOUCH) || hasListeners(CacheEventType.MUTATE)) {
            V cur = storeGet(key);
            if (hasListeners(CacheEventType.TOUCH)) fire(CacheEventType.TOUCH, key, cur, cur);
            if (hasListeners(CacheEventType.MUTATE)) fire(CacheEventType.MUTATE, key, cur, cur);
        }
    }

    @Override
    public Duration ttl(K key) {
        return storeTtl(key);
    }

    // ========== 删除 ==========

    @Override
    public V remove(K key) {
        // 不存在或已过期（逻辑删除）：与 get/containsKey 一致，视为不存在，静默无操作
        if (!storeContains(key)) return null;
        V old = storeRemove(key);
        if (old == null) return null; // 并发已删
        onRemove(key);
        onExplicitRemove(key);
        fire(CacheEventType.REMOVE, key, old, null);
        fire(CacheEventType.INVALIDATE, key, old, null);
        return old;
    }

    @Override
    public void clear() {
        storeClear();
        EvictionPolicy<K, V> p = policy;
        if (p != null) p.clear();
        if (hasListeners(CacheEventType.CLEAR)) fire(CacheEventType.CLEAR, null, null, null);
    }

    @Override
    public long approxCount() {
        return storeCount();
    }

    // ========== 策略回调（仅做空守卫后转发，具体语义由策略实现） ==========

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
     * 把单个过期 key 走删除管线：从存储移除 + 通知策略（onRemove + onExpire）+ 派发 EXPIRE/INVALIDATE 事件。
     * 返回是否真的删除了一个值（并发已删则返回 {@code false}）。
     * 惰性过期（{@link #get}）与主动扫描（{@link #sweepExpired}）共用此路径，保证删除语义唯一。
     */
    private boolean expireKey(K key) {
        V old = storeRemove(key);
        if (old == null) return false;
        onRemove(key);
        onExpire(key);
        fire(CacheEventType.EXPIRE, key, old, null);
        fire(CacheEventType.INVALIDATE, key, old, null);
        return true;
    }

    /**
     * 写入导致容量增长前的钩子：腾出容量、清理过期。
     * {@code capacity <= 0} 时无界，直接返回；否则先扫描过期、再驱动策略淘汰。
     * 容量淘汰时由本方法删除存储并派发 EVICT/INVALIDATE 事件；受害者由策略在
     * {@link EvictionPolicy#selectEvictVictim()} 内摘除。
     */
    private void ensureCapacity() {
        if (capacity <= 0) return;
        sweepExpired();
        if (!evicting.compareAndSet(false, true)) return; // 重入时跳过，避免重复淘汰
        try {
            EvictionPolicy<K, V> p = evictionPolicy();
            K victim;
            while (p != null && (victim = p.selectEvictVictim()) != null) {
                V old = storeRemove(victim);
                if (old != null) {
                    onRemove(victim);
                    onEvict(victim);
                    fire(CacheEventType.EVICT, victim, old, null);
                    fire(CacheEventType.INVALIDATE, victim, old, null);
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

    // ========== 可选：淘汰策略（EvictableCache） ==========

    @Override
    public void setEvictionPolicy(EvictionPolicy<K, V> policy) {
        this.policy = policy;
    }

    @Override
    public EvictionPolicy<K, V> evictionPolicy() {
        return policy;
    }

    // ========== 事件监听器（ObservableCache） ==========

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

    private boolean hasListeners(CacheEventType type) {
        List<CacheEventListener<? super K, ? super V>> list = listeners.get(type);
        return list != null && !list.isEmpty();
    }

    /** 写事件派发：具体类型（INSERT/UPDATE）与聚合 MUTATE 一并触发；无监听器时不读取或派发。 */
    private void fireUpsert(K key, V oldValue, V newValue, boolean existed) {
        CacheEventType specific = existed ? CacheEventType.UPDATE : CacheEventType.INSERT;
        if (hasListeners(specific)) fire(specific, key, oldValue, newValue);
        if (hasListeners(CacheEventType.MUTATE)) fire(CacheEventType.MUTATE, key, oldValue, newValue);
    }

    /**
     * 触发事件。约定：实际存储操作已完成之后才调用本方法，故监听器异常不影响已提交的业务逻辑。
     * 异常隔离：单个监听器异常被就地吞掉并继续派发其余监听器。
     * <p>
     * {@code oldValue} / {@code newValue} 为真实值，由各调用点在派发前通过 {@code if (hasListeners(type))}
     * 判断后才求值传入，故没有监听器时不会触发任何无谓的存储读写。
     */
    private void fire(CacheEventType type, K key, V oldValue, V newValue) {
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
