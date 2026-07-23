package com.flora.cache.store;

import com.flora.cache.CacheStore;

import java.time.Duration;

/**
 * 远程缓存抽象基类。
 * <p>
 * 实现 {@link CacheStore} 契约，负责 TTL 换算、null 参数校验、
 * 命名空间拼接等通用逻辑；具体的网络读写通过一组
 * {@code protected} 钩子方法留给子类。使用者只需用任意
 * Redis 客户端（Jedis / Lettuce / Redisson 等）实现这些钩子，
 * 即可获得一个完整的远程缓存实现：
 * </p>
 * <pre>{@code
 * RemoteCache cache = new RemoteCache("myapp:") {
 *     protected void doSet(String key, String value, long ttlMillis) {
 *         if (ttlMillis > 0) jedis.psetex(key, ttlMillis, value);
 *         else               jedis.set(key, value);
 *     }
 *     protected String doGet(String key)                  { return jedis.get(key); }
 *     protected boolean doSetNx(String key, String value, long ttlMillis) {
 *         String r = (ttlMillis > 0)
 *                 ? jedis.set(key, value, SetParams.setParams().nx().px(ttlMillis))
 *                 : jedis.set(key, value, SetParams.setParams().nx());
 *         return "OK".equals(r);
 *     }
 *     protected boolean doExpire(String key, long ttlMillis) { return jedis.pexpire(key, ttlMillis) == 1; }
 *     protected long doTtl(String key)                     { return jedis.pttl(key); }
 *     protected boolean doDelete(String key)               { return jedis.del(key) > 0; }
 *     protected boolean doExists(String key)               { return jedis.exists(key); }
 *     protected long doSize()                              { return jedis.dbSize(); }
 *     protected void doClear()                             { jedis.flushDB(); }
 * };
 * }</pre>
 *
 * <p>
 * 线程安全性取决于子类所用的客户端实现（主流 Redis 客户端的连接池
 * 都是线程安全的）。
 * </p>
 */
public abstract class RemoteCache implements CacheStore<String, String> {

    /** 永不过期标记：ttlMillis ≤ 0 表示写入永不过期的键 */
    protected static final long NO_EXPIRE = -1L;

    /** 命名空间前缀，可为空串；非空时所有 key 操作前自动拼接 */
    private final String namespace;

    protected RemoteCache() {
        this("");
    }

    protected RemoteCache(String namespace) {
        this.namespace = namespace == null ? "" : namespace;
    }

    // ========== 留口子：子类用 Redis 客户端实现以下钩子 ==========

    /**
     * 写入键值。{@code ttlMillis > 0} 时同时设置过期时间（毫秒）；
     * {@code ttlMillis == NO_EXPIRE} 时写入永不过期的键。
     */
    protected abstract void doSet(String key, String value, long ttlMillis);

    /**
     * 读取键值，不存在返回 {@code null}。
     */
    protected abstract String doGet(String key);

    /**
     * 仅当 key 不存在时写入（SET NX），{@code ttlMillis} 语义同 {@link #doSet}。
     *
     * @return 是否写入成功
     */
    protected abstract boolean doSetNx(String key, String value, long ttlMillis);

    /**
     * 为已存在的 key 设置过期时间（毫秒）。
     *
     * @return 是否设置成功（key 存在）
     */
    protected abstract boolean doExpire(String key, long ttlMillis);

    /**
     * 查询 key 的剩余过期时间（毫秒）。
     *
     * @return 剩余毫秒数；key 不存在返回 -2，key 存在但永不过期返回 -1
     *         （与 Redis TTL/PTTL 命令语义一致）
     */
    protected abstract long doTtl(String key);

    /**
     * 删除 key。
     *
     * @return 是否删除了已存在的 key
     */
    protected abstract boolean doDelete(String key);

    /**
     * key 是否存在（含已过期但未惰性删除的键，由后端语义决定）。
     */
    protected abstract boolean doExists(String key);

    /**
     * 当前键数量（如 Redis DBSIZE）。
     */
    protected abstract long doSize();

    /**
     * 清空全部键（如 Redis FLUSHDB）。
     */
    protected abstract void doClear();

    // ========== 可覆盖的扩展点 ==========

    /**
     * 拼接命名空间前缀。子类可覆盖以自定义 key 编码（如哈希、序列化）。
     */
    protected String wrapKey(String key) {
        return namespace + key;
    }

    // ========== CacheStore 实现 ==========

    @Override
    public void put(String key, String value) {
        if (value == null) {
            throw new NullPointerException("value must not be null");
        }
        doSet(wrapKey(key), value, NO_EXPIRE);
    }

    @Override
    public boolean putIfAbsent(String key, String value) {
        if (value == null) {
            throw new NullPointerException("value must not be null");
        }
        return doSetNx(wrapKey(key), value, NO_EXPIRE);
    }

    @Override
    public void put(String key, String value, Duration duration) {
        if (value == null) {
            throw new NullPointerException("value must not be null");
        }
        if (duration == null) {
            put(key, value);
            return;
        }
        if (duration.isZero() || duration.isNegative()) {
            remove(key);
            return;
        }
        doSet(wrapKey(key), value, duration.toMillis());
    }

    @Override
    public boolean putIfAbsent(String key, String value, Duration duration) {
        if (value == null) {
            throw new NullPointerException("value must not be null");
        }
        if (duration == null) {
            return putIfAbsent(key, value);
        }
        if (duration.isZero() || duration.isNegative()) {
            return false;
        }
        return doSetNx(wrapKey(key), value, duration.toMillis());
    }

    @Override
    public String get(String key) {
        return doGet(wrapKey(key));
    }

    @Override
    public void setTtl(String key, Duration duration) {
        if (duration == null) return;
        if (duration.isZero() || duration.isNegative()) {
            remove(key);
            return;
        }
        doExpire(wrapKey(key), duration.toMillis());
    }

    @Override
    public Duration ttl(String key) {
        long millis = doTtl(wrapKey(key));
        if (millis == -2L) return null;            // key 不存在
        if (millis < 0L) return Duration.ZERO;     // 永不过期
        return Duration.ofMillis(millis);
    }

    @Override
    public void remove(String key) {
        doDelete(wrapKey(key));
    }

    @Override
    public void clear() {
        doClear();
    }

    @Override
    public long approxCount() {
        return doSize();
    }

    @Override
    public boolean containsKey(String key) {
        return doExists(wrapKey(key));
    }
}
