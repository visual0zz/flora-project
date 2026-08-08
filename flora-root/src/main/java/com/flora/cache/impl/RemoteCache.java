package com.flora.cache.impl;

import com.flora.cache.interfaces.Cache;
import com.flora.cache.interfaces.MemoryCache;
import com.flora.cache.interfaces.RemoteStore;

import java.util.Objects;

/**
 * 远程缓存（Redis 等），键与值均为 {@code String}，与 Redis 协议对应。
 * <p>通过 {@code RemoteCache.of(store)} 代理一个 {@link RemoteStore} 后端，本类只承载
 * {@link Cache} 契约的语义换算（{@code Duration} ↔ 毫秒、TTL 约定等），实际读写委托给 store。
 * 远端过期与淘汰由后端（如 Redis maxmemory-policy）管理，故不承载容量维度、淘汰策略与本地过期扫描，
 * 也不区分 {@code INSERT}/{@code UPDATE}（每次 put 统一写入）。线程安全性取决于 store 实现。</p>
 * <p>本类只实现最小契约 {@link Cache}，<b>不管理任何监听器</b>。如需事件监听，请用可观测装饰器
 * （{@code CacheListenerAdapter.of(this)}）包一层，调用方即可 {@code addListener} 订阅事件。
 * 装饰器仅在公开 API 面（put / putIfAbsent / remove / clear / setTtl）上派发事件；
 * 远端自身的淘汰 / 过期由后端驱动，不经过本地装饰器，故不会派发对应事件。
 * （内部移除钩子 {@link MemoryCache#setInternalRemovalListener} 仅由本地托管的
 * {@link MemoryCache} 实现（如 {@link ConcurrentHashMapCache}）持有；本类不实现该接口，
 * 无法桥接远端驱动的 EVICT / EXPIRE，故其不可观测。）</p>
 *
 * <pre>{@code
 * RemoteStore store = new RemoteStore() {
 *     @Override public void set(String key, String value, long ttlMillis) {
 *         if (ttlMillis == -1) jedis.set(key, value);
 *         else jedis.set(key, value, "PX", ttlMillis);
 *     }
 *     // ... 其余方法按 Redis 命令实现
 * };
 * RemoteCache cache = RemoteCache.of(store);
 * // 需要可观测时再装饰：
 * ObservableCache<String, String> obs = CacheListenerAdapter.of(cache);
 * }</pre>
 */
public final class RemoteCache implements Cache<String, String> {

    private final RemoteStore store;

    private RemoteCache(RemoteStore store) {
        this.store = Objects.requireNonNull(store, "store must not be null");
    }

    /** 代理指定远程存储后端，返回完整远程缓存。 */
    public static RemoteCache of(RemoteStore store) {
        return new RemoteCache(store);
    }

    // ========== 写入 ==========

    @Override
    public void put(String key, String value) {
        Objects.requireNonNull(value, "value must not be null");
        store.set(key, value, -1L);
    }

    @Override
    public void put(String key, String value, java.time.Duration duration) {
        Objects.requireNonNull(value, "value must not be null");
        if (duration == null) {
            put(key, value);
            return;
        }
        if (duration.isZero() || duration.isNegative()) {
            remove(key);
            return;
        }
        long ttlMillis = duration.equals(java.time.Duration.MAX) ? -1L : duration.toMillis();
        store.set(key, value, ttlMillis);
    }

    @Override
    public boolean putIfAbsent(String key, String value) {
        Objects.requireNonNull(value, "value must not be null");
        return store.setNx(key, value, -1L);
    }

    @Override
    public boolean putIfAbsent(String key, String value, java.time.Duration duration) {
        Objects.requireNonNull(value, "value must not be null");
        if (duration != null && (duration.isZero() || duration.isNegative())) return false;
        long ttlMillis = (duration == null || duration.equals(java.time.Duration.MAX)) ? -1L : duration.toMillis();
        return store.setNx(key, value, ttlMillis);
    }

    // ========== 读取 ==========

    @Override
    public String get(String key) {
        return store.get(key);
    }

    @Override
    public boolean containsKey(String key) {
        return store.exists(key);
    }

    // ========== TTL 管理 ==========

    @Override
    public void setTtl(String key, java.time.Duration duration) {
        if (duration == null) return;
        if (duration.isZero() || duration.isNegative()) {
            remove(key);
            return;
        }
        if (!store.exists(key)) return; // 不复活缺失/过期键
        long ttlMillis = duration.equals(java.time.Duration.MAX) ? -1L : duration.toMillis();
        store.expire(key, ttlMillis);
    }

    @Override
    public java.time.Duration ttl(String key) {
        long millis = store.ttl(key);
        if (millis == -1) return java.time.Duration.MAX;     // 存在但无过期（Redis 语义）
        if (millis < 0) return java.time.Duration.ZERO;      // 键不存在（Redis 返回 -2）
        return java.time.Duration.ofMillis(millis);          // 剩余毫秒
    }

    // ========== 删除 ==========

    @Override
    public String remove(String key) {
        if (!store.exists(key)) return null;
        String old = store.get(key);
        store.delete(key);
        return old;
    }

    @Override
    public void clear() {
        store.clear();
    }

    @Override
    public long approxCount() {
        return store.size();
    }
}
