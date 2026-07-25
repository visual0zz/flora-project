package com.flora.cache.store;

import com.flora.cache.eviction.WTinyLfuEvictionPolicy;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * W-TinyLFU + TTL 的本地内存缓存。
 * <p>
 * 继承 {@link BoundedCacheSupport}，基于 {@link java.util.concurrent.ConcurrentHashMap} 维护 KV 与过期，
 * 并在构造时挂载 {@link WTinyLfuEvictionPolicy} 作为淘汰策略；更换策略可调用 {@link #setEvictionPolicy}。
 * <p>
 * 过期采用惰性删除（{@code rawGet} 时发现过期即移除）+ 主动扫描（{@code cleanUp()} 触发 {@code EXPIRE} 事件）。
 * {@code capacity <= 0} 时为无界模式：策略不参与淘汰。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public class MemoryCache<K, V> extends BoundedCacheSupport<K, V> {

    /** 永不过期标记：expiry 映射中不存在即表示永不过期 */
    private static final long IMMORTAL = 0L;

    private final ConcurrentHashMap<K, V> map = new ConcurrentHashMap<>();
    /** key → 绝对过期时间戳(ms)；不存在表示永不过期 */
    private final ConcurrentHashMap<K, Long> expiry = new ConcurrentHashMap<>();

    public MemoryCache() {
        this(-1);
    }

    /**
     * @param capacity 容量上限（{@code <=0} 表示无上限，自带 W-TinyLFU 休眠、永不淘汰）
     */
    public MemoryCache(long capacity) {
        super(capacity);
        setEvictionPolicy(new WTinyLfuEvictionPolicy<>(capacity, this::approxCount));
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private boolean expired(long exp) {
        return exp != IMMORTAL && now() >= exp;
    }

    private Long computeExpiry(Duration d) {
        if (d == null || d.isZero() || d.isNegative()) return null;
        return now() + d.toMillis();
    }

    // ========== 原始存储钩子 ==========

    @Override
    protected void rawPut(K key, V value) {
        map.put(key, value);
        expiry.remove(key);
    }

    @Override
    protected void rawPut(K key, V value, Duration duration) {
        map.put(key, value);
        Long exp = computeExpiry(duration);
        if (exp == null) expiry.remove(key);
        else expiry.put(key, exp);
    }

    @Override
    protected boolean rawPutIfAbsent(K key, V value) {
        return map.putIfAbsent(key, value) == null;
    }

    @Override
    protected boolean rawPutIfAbsent(K key, V value, Duration duration) {
        Long exp = computeExpiry(duration);
        return map.computeIfAbsent(key, _ -> {
            if (exp != null) expiry.put(key, exp);
            return value;
        }) == value;
    }

    @Override
    protected V rawGet(K key) {
        V v = map.get(key);
        if (v == null) return null;
        Long exp = expiry.get(key);
        if (exp != null && expired(exp)) {
            // 惰性过期：只隐藏过期值，不在此删除；真正的删除由引擎管线
            //（CacheSupport.get → expireKey）负责，以保证策略索引与 EXPIRE 事件一致。
            return null;
        }
        return v;
    }

    @Override
    protected V rawRemove(K key) {
        V old = map.remove(key);
        expiry.remove(key);
        return old;
    }

    @Override
    protected boolean rawContains(K key) {
        V v = map.get(key);
        if (v == null) return false;
        Long exp = expiry.get(key);
        return exp == null || !expired(exp);
    }

    @Override
    protected Duration rawTtl(K key) {
        Long exp = expiry.get(key);
        if (exp == null) return Duration.ZERO;
        long remaining = exp - now();
        return remaining > 0 ? Duration.ofMillis(remaining) : Duration.ZERO;
    }

    @Override
    protected void rawSetTtl(K key, Duration duration) {
        // duration 已由引擎保证为正数（零/负已在 CacheSupport.setTtl 走 expireKey 路径）
        if (duration == null) return;
        if (map.containsKey(key)) {
            expiry.put(key, now() + duration.toMillis());
        }
    }

    @Override
    protected void rawClear() {
        map.clear();
        expiry.clear();
    }

    @Override
    protected Iterable<K> rawKeys() {
        return map.keySet();
    }

    @Override
    protected boolean rawIsExpired(K key) {
        Long exp = expiry.get(key);
        return exp != null && expired(exp);
    }

    @Override
    protected long rawCount() {
        return map.mappingCount();
    }
}
