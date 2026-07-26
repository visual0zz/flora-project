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
 *     protected void doSet(String key, String value, long ttlMillis) {
 *         if (ttlMillis == -1) jedis.set(key, value);
 *         else jedis.set(key, value, "PX", ttlMillis);
 *     }
 *     protected String doGet(String key)                          { return jedis.get(key); }
 *     protected boolean doSetNx(String key, String value, long ttlMillis) {
 *         return ttlMillis == -1 ? jedis.set(key, value, "NX") != null
 *                                 : jedis.set(key, value, "NX", "PX", ttlMillis) != null;
 *     }
 *     protected boolean doExpire(String key, long ttlMillis) {
 *         return ttlMillis == -1 ? jedis.persist(key) == 1 : jedis.pexpire(key, ttlMillis) == 1;
 *     }
 *     protected long doTtl(String key)                            { return jedis.pttl(key); } // -2 缺失 / -1 无过期
 *     protected long doDelete(String key)                        { return jedis.del(key); }
 *     protected boolean doExists(String key)                     { return jedis.exists(key); }
 *     protected long doSize()                                     { return jedis.dbSize(); }
 *     protected void doClear()                                    { jedis.flushDB(); }
 * };
 * }</pre>
 *
 * 钩子约定（全部对齐 Redis 命令语义）：
 * <ul>
 *   <li>{@code ttlMillis}：{@code -1} = 持久化（对应 Redis 的「无 PX」/ {@code PERSIST}，亦对应公开 API 的
 *       {@code Duration.MAX}）；{@code > 0} = 设置该毫秒级过期（{@code PEXPIRE}）；
 *       {@code <= 0} 且 {@code != -1}（即 0 或负）= 立即删除该键（与 Redis 非正过期即删除一致）。</li>
 *   <li>{@code doTtl}：键缺失返回 {@code -2}；存在但无过期返回 {@code -1}；其余返回剩余毫秒（{@code > 0}）。</li>
 *   <li>{@code doDelete}：返回被删除的键数量（Redis {@code DEL}，单键为 0 或 1）。</li>
 *   <li>{@code doExists}：返回键是否存在（Redis {@code EXISTS}，1/0）。</li>
 * </ul>
 */
public abstract class RemoteCache implements ObservableCache<String, String> {

    /** 事件引擎：复用 CacheListenerAdapter 的监听器注册与派发能力。 */
    private final CacheListenerAdapter<String, String> observer = new CacheListenerAdapter<>(this);

    // ========== 子类钩子 ==========

    /** 写入键值（对应 Redis {@code SET}）：{@code ttlMillis} 见类级约定——{@code -1} 持久化，{@code > 0} 设过期，非正非 -1 即删除。 */
    protected abstract void doSet(String key, String value, long ttlMillis);

    /** 读取键值（对应 Redis {@code GET}）；缺失返回 {@code null}。 */
    protected abstract String doGet(String key);

    /** 仅当 key 不存在时写入（对应 Redis {@code SET key value NX [PX ttlMillis]}）；
     *  {@code ttlMillis} 语义同 {@link #doSet}；返回是否写入成功（OK / nil）。 */
    protected abstract boolean doSetNx(String key, String value, long ttlMillis);

    /** 刷新 key 的过期时长（对应 Redis {@code PEXPIRE} / {@code PERSIST}）：
     *  {@code ttlMillis == -1} 表示持久化（{@code PERSIST} 移除过期），{@code > 0} 设置过期（{@code PEXPIRE}），
     *  非正非 -1 表示立即删除（{@code EXPIRE 0}）；返回键是否存在（{@code PEXPIRE}/{@code PERSIST} 的 1/0）。 */
    protected abstract boolean doExpire(String key, long ttlMillis);

    /** 查询剩余过期毫秒（Redis 语义）：键缺失返回 {@code -2}，存在但无过期返回 {@code -1}，否则返回剩余毫秒。 */
    protected abstract long doTtl(String key);

    /** 删除 key（对应 Redis {@code DEL}）；返回被删除的键数量（单键为 {@code 0} 或 {@code 1}）。 */
    protected abstract long doDelete(String key);

    /** key 是否存在（对应 Redis {@code EXISTS}，1/0）。 */
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
        doSet(key, value, -1L);
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
        long ttlMillis = duration.equals(java.time.Duration.MAX) ? -1L : duration.toMillis();
        doSet(key, value, ttlMillis);
        CacheEventType specific = existed ? CacheEventType.UPDATE : CacheEventType.INSERT;
        observer.fire(specific, key, null, value);
        observer.fire(CacheEventType.MUTATE, key, null, value);
    }

    @Override
    public boolean putIfAbsent(String key, String value) {
        Objects.requireNonNull(value, "value must not be null");
        boolean inserted = doSetNx(key, value, -1L);
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
        long ttlMillis = (duration == null || duration.equals(java.time.Duration.MAX)) ? -1L : duration.toMillis();
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
        long ttlMillis = duration.equals(java.time.Duration.MAX) ? -1L : duration.toMillis();
        doExpire(key, ttlMillis);
        observer.fire(CacheEventType.TOUCH, key, null, null);
        observer.fire(CacheEventType.MUTATE, key, null, null);
    }

    @Override
    public java.time.Duration ttl(String key) {
        long millis = doTtl(key);
        if (millis == -1) return java.time.Duration.MAX;     // 存在但无过期（Redis 语义）
        if (millis < 0) return java.time.Duration.ZERO;      // 键不存在（Redis 返回 -2）
        return java.time.Duration.ofMillis(millis);          // 剩余毫秒
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
