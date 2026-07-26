package com.flora.cache.store;

import com.flora.cache.CacheEventType;
import com.flora.cache.CacheEventListener;
import com.flora.cache.ObservableCache;

import java.util.Objects;

/**
 * 远程缓存（Redis 等）抽象基类，键与值均为 {@code String}，与 Redis 协议对应。
 * <p>
 * 子类用具体 Redis 客户端实现 {@code doXxx} 钩子即可获得完整远程缓存。本类刻意精简：
 * 远端过期与淘汰由后端（如 Redis maxmemory-policy）管理，故不承载容量维度、淘汰策略与本地过期扫描，
 * 也不区分 {@code INSERT}/{@code UPDATE}（每次 put 统一派发 INSERT）。线程安全性取决于子类所用客户端。
 * <p>
 * 本类实现 {@link ObservableCache}，事件派发复用 {@link CacheListenerAdapter} 引擎，
 * 因此可像本地缓存一样注册监听器（{@code addListener}/{@code removeListener}）。
 *
 * <pre>{@code
 * RemoteCache cache = new RemoteCache("myapp:") {
 *     protected void doSet(String key, String value, long ttlMillis) { jedis.set(key, value); }
 *     protected String doGet(String key)                          { return jedis.get(key); }
 *     protected boolean doSetNx(String key, String value, long ttlMillis) { ... }
 *     protected boolean doExpire(String key, long ttlMillis)      { ... }
 *     protected long doTtl(String key)                            { ... }
 *     protected boolean doDelete(String key)                      { ... }
 *     protected boolean doExists(String key)                      { ... }
 *     protected long doSize()                                     { ... }
 *     protected void doClear()                                    { ... }
 * };
 * }</pre>
 *
 * 钩子约定：
 * <ul>
 *   <li>{@code ttlMillis <= 0}：持久化（不设过期）；{@code > 0}：毫秒级过期时长。</li>
 *   <li>{@code doTtl}：键缺失返回负数；永不过期返回 {@code 0}；否则返回剩余毫秒。</li>
 * </ul>
 */
public abstract class RemoteCache implements ObservableCache<String, String> {

    /** 事件引擎：复用 CacheListenerAdapter 的监听器注册与派发能力。 */
    private final CacheListenerAdapter<String, String> observer = new CacheListenerAdapter<>(this);

    // ========== 子类钩子 ==========

    /** 写入键值；{@code ttlMillis <= 0} 表示持久化（不设过期）。 */
    protected abstract void doSet(String key, String value, long ttlMillis);

    /** 读取键值；缺失返回 {@code null}（与 {@link #get} 语义一致）。 */
    protected abstract String doGet(String key);

    /** 仅当 key 不存在时写入；返回是否写入成功（Redis SETNX 语义，含 TTL）。 */
    protected abstract boolean doSetNx(String key, String value, long ttlMillis);

    /** 刷新 key 的过期时长；{@code ttlMillis <= 0} 表示持久化；返回是否成功（key 存在）。 */
    protected abstract boolean doExpire(String key, long ttlMillis);

    /** 查询剩余过期毫秒；键缺失返回负数，永不过期返回 {@code 0}，否则剩余毫秒。 */
    protected abstract long doTtl(String key);

    /** 删除 key；返回是否真的删除了一个存在的 key。 */
    protected abstract boolean doDelete(String key);

    /** key 是否存在（未过期）。 */
    protected abstract boolean doExists(String key);

    /** 当前条目数量近似值。 */
    protected abstract long doSize();

    /** 清空所有条目。 */
    protected abstract void doClear();

    // ========== ObservableCache：委托给事件引擎 ==========

    @Override
    public void addListener(CacheEventType type, CacheEventListener<? super String, ? super String> listener) {
        observer.addListener(type, listener);
    }

    @Override
    public void removeListener(CacheEventType type, CacheEventListener<? super String, ? super String> listener) {
        observer.removeListener(type, listener);
    }

    @Override
    public void removeListeners(CacheEventType type) {
        observer.removeListeners(type);
    }

    // ========== 写入 ==========

    @Override
    public void put(String key, String value) {
        Objects.requireNonNull(value, "value must not be null");
        boolean existed = doExists(key);
        doSet(key, value, 0L);
        CacheEventType specific = existed ? CacheEventType.UPDATE : CacheEventType.INSERT;
        observer.fire(specific, key, null, value);
        observer.fire(CacheEventType.MUTATE, key, null, value);
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
        boolean existed = doExists(key);
        long ttlMillis = duration.equals(java.time.Duration.MAX) ? 0L : duration.toMillis();
        doSet(key, value, ttlMillis);
        CacheEventType specific = existed ? CacheEventType.UPDATE : CacheEventType.INSERT;
        observer.fire(specific, key, null, value);
        observer.fire(CacheEventType.MUTATE, key, null, value);
    }

    @Override
    public boolean putIfAbsent(String key, String value) {
        Objects.requireNonNull(value, "value must not be null");
        boolean inserted = doSetNx(key, value, 0L);
        if (inserted) {
            observer.fire(CacheEventType.INSERT, key, null, value);
            observer.fire(CacheEventType.MUTATE, key, null, value);
        }
        return inserted;
    }

    @Override
    public boolean putIfAbsent(String key, String value, java.time.Duration duration) {
        Objects.requireNonNull(value, "value must not be null");
        if (duration != null && (duration.isZero() || duration.isNegative())) return false;
        long ttlMillis = (duration == null || duration.equals(java.time.Duration.MAX)) ? 0L : duration.toMillis();
        boolean inserted = doSetNx(key, value, ttlMillis);
        if (inserted) {
            observer.fire(CacheEventType.INSERT, key, null, value);
            observer.fire(CacheEventType.MUTATE, key, null, value);
        }
        return inserted;
    }

    // ========== 读取 ==========

    @Override
    public String get(String key) {
        return doGet(key);
    }

    @Override
    public boolean containsKey(String key) {
        return doExists(key);
    }

    // ========== TTL 管理 ==========

    @Override
    public void setTtl(String key, java.time.Duration duration) {
        if (duration == null) return;
        if (duration.isZero() || duration.isNegative()) {
            remove(key);
            return;
        }
        if (!doExists(key)) return; // 不复活缺失/过期键
        long ttlMillis = duration.equals(java.time.Duration.MAX) ? 0L : duration.toMillis();
        doExpire(key, ttlMillis);
        observer.fire(CacheEventType.TOUCH, key, null, null);
        observer.fire(CacheEventType.MUTATE, key, null, null);
    }

    @Override
    public java.time.Duration ttl(String key) {
        long millis = doTtl(key);
        if (millis < 0) return java.time.Duration.ZERO;     // 不存在/已过期
        if (millis == 0) return java.time.Duration.MAX;      // 永不过期
        return java.time.Duration.ofMillis(millis);
    }

    // ========== 删除 ==========

    @Override
    public String remove(String key) {
        if (!doExists(key)) return null;
        String old = doGet(key);
        doDelete(key);
        observer.fire(CacheEventType.REMOVE, key, old, null);
        observer.fire(CacheEventType.INVALIDATE, key, old, null);
        return old;
    }

    @Override
    public void clear() {
        doClear();
        observer.fire(CacheEventType.CLEAR, null, null, null);
    }

    @Override
    public long approxCount() {
        return doSize();
    }
}
