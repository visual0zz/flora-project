package com.flora.cache.store;

import com.flora.cache.CacheStore;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 {@link ConcurrentHashMap} 的纯存储实现：仅负责 KV 与 TTL，
 * 不含任何淘汰逻辑。作为与 {@link com.flora.cache.EvictionPolicy} 自由组合的积木。
 * <p>
 * 过期采用惰性删除（{@code get} 时发现过期即移除）+ 主动扫描（{@code keys()}/{@code isExpired()}
 * 供组合层的 {@code gc()} 使用）。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public final class ConcurrentHashMapStore<K, V> implements CacheStore<K, V> {

    /** 永不过期标记：expiry 映射中不存在即表示永不过期 */
    private static final long IMMORTAL = 0L;

    private final ConcurrentHashMap<K, V> map = new ConcurrentHashMap<>();
    /** key → 绝对过期时间戳(ms)；不存在表示永不过期 */
    private final ConcurrentHashMap<K, Long> expiry = new ConcurrentHashMap<>();

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

    // ========== 写入 ==========

    @Override
    public void put(K key, V value) {
        map.put(key, value);
        expiry.remove(key);
    }

    @Override
    public boolean putIfAbsent(K key, V value) {
        return map.putIfAbsent(key, value) == null;
    }

    @Override
    public void put(K key, V value, Duration duration) {
        map.put(key, value);
        Long exp = computeExpiry(duration);
        if (exp == null) expiry.remove(key);
        else expiry.put(key, exp);
    }

    @Override
    public boolean putIfAbsent(K key, V value, Duration duration) {
        Long exp = computeExpiry(duration);
        return map.computeIfAbsent(key, _ -> {
            if (exp != null) expiry.put(key, exp);
            return value;
        }) == value;
    }

    // ========== 读取 ==========

    @Override
    public V get(K key) {
        V v = map.get(key);
        if (v == null) return null;
        Long exp = expiry.get(key);
        if (exp != null && expired(exp)) {
            map.remove(key, v);   // 惰性过期
            expiry.remove(key);
            return null;
        }
        return v;
    }

    // ========== TTL 管理 ==========

    @Override
    public void setTtl(K key, Duration duration) {
        if (duration == null) return;
        if (duration.isZero() || duration.isNegative()) {
            remove(key);
            return;
        }
        if (map.containsKey(key)) {
            expiry.put(key, now() + duration.toMillis());
        }
    }

    @Override
    public Duration ttl(K key) {
        Long exp = expiry.get(key);
        if (exp == null) return Duration.ZERO;
        long remaining = exp - now();
        return remaining > 0 ? Duration.ofMillis(remaining) : Duration.ZERO;
    }

    // ========== 删除 ==========

    @Override
    public V remove(K key) {
        V old = map.remove(key);
        expiry.remove(key);
        return old;
    }

    @Override
    public void clear() {
        map.clear();
        expiry.clear();
    }

    // ========== 查询 ==========

    @Override
    public long approxCount() {
        return map.mappingCount();
    }

    @Override
    public boolean containsKey(K key) {
        V v = map.get(key);
        if (v == null) return false;
        Long exp = expiry.get(key);
        return exp == null || !expired(exp);
    }

    // ========== 组合层支撑 ==========

    @Override
    public Iterable<K> keys() {
        return map.keySet();
    }

    @Override
    public boolean isExpired(K key) {
        Long exp = expiry.get(key);
        return exp != null && expired(exp);
    }
}
