package com.flora.cache.store;

import com.flora.cache.CacheStore;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 {@link ConcurrentHashMap} 的本地存储实现，仅负责 KV 与 TTL，
 * 不含任何淘汰逻辑。作为与 {@link com.flora.cache.EvictionPolicy} 自由组合的
 * 原始存储积木：直接使用时是无界缓存；通过 {@link #setEvictionPolicy} 挂载策略即变有界。
 * <p>
 * 过期采用惰性删除（{@code rawGet} 时发现过期即移除）+ 主动扫描（{@code rawKeys()}/
 * {@code rawIsExpired()} 供内部 {@code gc()} 使用）。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public class ConcurrentHashMapStore<K, V> extends AbstractCacheStore<K, V> {

    /** 永不过期标记：expiry 映射中不存在即表示永不过期 */
    private static final long IMMORTAL = 0L;

    private final ConcurrentHashMap<K, V> map = new ConcurrentHashMap<>();
    /** key → 绝对过期时间戳(ms)；不存在表示永不过期 */
    private final ConcurrentHashMap<K, Long> expiry = new ConcurrentHashMap<>();

    public ConcurrentHashMapStore() {
        super(-1);
    }

    /**
     * @param capacity 容量上限（{@code <=0} 表示无上限）
     */
    public ConcurrentHashMapStore(long capacity) {
        super(capacity);
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
            map.remove(key, v);   // 惰性过期
            expiry.remove(key);
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
        if (duration == null) return;
        if (duration.isZero() || duration.isNegative()) {
            rawRemove(key);
            return;
        }
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
