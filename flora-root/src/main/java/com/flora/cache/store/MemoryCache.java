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
import java.util.concurrent.ConcurrentHashMap;

/**
 * W-TinyLFU + TTL 的本地内存缓存。
 * <p>
 * 直接实现 {@link RawStore} 提供本地 KV/TTL 存储钩子，组合 {@link CacheEngine} 获得完整的
 * 缓存行为（读写、事件、可挂策略、容量约束）；构造时挂载 {@link WTinyLfuEvictionPolicy} 作为淘汰策略，
 * 更换策略可调用 {@link #setEvictionPolicy}。
 * <p>
 * 过期采用惰性删除（{@code rawGet} 时发现过期即隐藏）+ 主动扫描（{@code cleanUp()} 触发 {@code EXPIRE} 事件）。
 * {@code capacity <= 0} 时为无界模式：策略不参与淘汰。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public class MemoryCache<K, V>
        implements RawStore<K, V>, Cache<K, V>, ObservableCache<K, V>, EvictableCache<K, V>, BoundedCache<K, V> {

    private final ConcurrentHashMap<K, V> map = new ConcurrentHashMap<>();
    /** key → 绝对过期时间戳(ms)；不存在表示永不过期 */
    private final ConcurrentHashMap<K, Long> expiry = new ConcurrentHashMap<>();

    private final CacheEngine<K, V> engine;

    public MemoryCache() {
        this(-1);
    }

    /**
     * @param capacity 容量上限（{@code <=0} 表示无上限，自带 W-TinyLFU 休眠、永不淘汰）
     */
    public MemoryCache(long capacity) {
        this.engine = new CacheEngine<>(this, capacity);
        setEvictionPolicy(new WTinyLfuEvictionPolicy<>(capacity, this::approxCount));
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private boolean expired(long exp) {
        return now() >= exp;
    }

    private Long computeExpiry(Duration d) {
        if (d == null || d.isNegative()) return null; // 不设置过期时间（永不过期）
        if (d.equals(Duration.MAX)) return null;      // 过期时间无限（永不过期）
        return now() + d.toMillis();                  // 正数：绝对过期时间戳（ZERO 视为立即过期）
    }

    // ========== 原始存储钩子（实现 RawStore） ==========

    @Override
    public void rawPut(K key, V value) {
        map.put(key, value);
        expiry.remove(key);
    }

    @Override
    public void rawPut(K key, V value, Duration duration) {
        map.put(key, value);
        Long exp = computeExpiry(duration);
        if (exp == null) expiry.remove(key);
        else expiry.put(key, exp);
    }

    @Override
    public boolean rawPutIfAbsent(K key, V value) {
        return map.putIfAbsent(key, value) == null;
    }

    @Override
    public boolean rawPutIfAbsent(K key, V value, Duration duration) {
        Long exp = computeExpiry(duration);
        return map.computeIfAbsent(key, _ -> {
            if (exp != null) expiry.put(key, exp);
            return value;
        }) == value;
    }

    @Override
    public V rawGet(K key) {
        V v = map.get(key);
        if (v == null) return null;
        Long exp = expiry.get(key);
        if (exp != null && expired(exp)) {
            // 惰性过期：只隐藏过期值，不在此删除；真正的删除由引擎管线
            //（CacheEngine.get → expireKey）负责，以保证策略索引与 EXPIRE 事件一致。
            return null;
        }
        return v;
    }

    @Override
    public V rawRemove(K key) {
        V old = map.remove(key);
        expiry.remove(key);
        return old;
    }

    @Override
    public boolean rawContains(K key) {
        V v = map.get(key);
        if (v == null) return false;
        Long exp = expiry.get(key);
        return exp == null || !expired(exp);
    }

    @Override
    public Duration rawTtl(K key) {
        if (!map.containsKey(key)) return Duration.ZERO; // 不存在
        Long exp = expiry.get(key);
        if (exp == null) return Duration.MAX;            // 永不过期
        long remaining = exp - now();
        return remaining > 0 ? Duration.ofMillis(remaining) : Duration.ZERO; // 已过期 → ZERO
    }

    @Override
    public void rawSetTtl(K key, Duration duration) {
        // duration 已由引擎保证为非 ZERO/非 NEGATIVE；MAX 表示永不过期（移除 TTL）
        if (duration == null) return;
        if (!map.containsKey(key)) return;
        if (duration.equals(Duration.MAX)) {
            expiry.remove(key); // 永不过期
        } else {
            expiry.put(key, now() + duration.toMillis());
        }
    }

    @Override
    public void rawClear() {
        map.clear();
        expiry.clear();
    }

    @Override
    public Iterable<K> rawKeys() {
        return map.keySet();
    }

    @Override
    public boolean rawIsExpired(K key) {
        Long exp = expiry.get(key);
        return exp != null && expired(exp);
    }

    @Override
    public long rawCount() {
        return map.mappingCount();
    }

    // ========== Cache ==========

    @Override
    public void put(K key, V value) {
        engine.put(key, value);
    }

    @Override
    public boolean putIfAbsent(K key, V value) {
        return engine.putIfAbsent(key, value);
    }

    @Override
    public void put(K key, V value, Duration duration) {
        engine.put(key, value, duration);
    }

    @Override
    public boolean putIfAbsent(K key, V value, Duration duration) {
        return engine.putIfAbsent(key, value, duration);
    }

    @Override
    public V get(K key) {
        return engine.get(key);
    }

    @Override
    public void setTtl(K key, Duration duration) {
        engine.setTtl(key, duration);
    }

    @Override
    public Duration ttl(K key) {
        return engine.ttl(key);
    }

    @Override
    public V remove(K key) {
        return engine.remove(key);
    }

    @Override
    public void clear() {
        engine.clear();
    }

    @Override
    public long approxCount() {
        return engine.approxCount();
    }

    @Override
    public boolean containsKey(K key) {
        return engine.containsKey(key);
    }

    // ========== ObservableCache ==========

    @Override
    public void addListener(CacheEventType type, CacheEventListener<? super K, ? super V> listener) {
        engine.addListener(type, listener);
    }

    @Override
    public void removeListener(CacheEventType type, CacheEventListener<? super K, ? super V> listener) {
        engine.removeListener(type, listener);
    }

    @Override
    public void removeListeners(CacheEventType type) {
        engine.removeListeners(type);
    }

    // ========== EvictableCache ==========

    @Override
    public void setEvictionPolicy(EvictionPolicy<K, V> policy) {
        engine.setEvictionPolicy(policy);
    }

    @Override
    public EvictionPolicy<K, V> evictionPolicy() {
        return engine.evictionPolicy();
    }

    // ========== BoundedCache ==========

    @Override
    public long cleanUp() {
        return engine.cleanUp();
    }

    @Override
    public boolean isFull() {
        return engine.isFull();
    }

    @Override
    public long capacity() {
        return engine.capacity();
    }
}
