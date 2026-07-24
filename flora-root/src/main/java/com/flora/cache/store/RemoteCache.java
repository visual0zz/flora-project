package com.flora.cache.store;

import com.flora.cache.EvictionPolicy;

import java.time.Duration;
import java.util.Collections;

/**
 * 远程缓存抽象基类。
 * <p>
 * 继承 {@link AbstractCacheStore} 复用容量约束、事件派发与策略回调逻辑；子类只需用
 * 任意 Redis 客户端实现一组 {@code doXxx} 钩子即可获得完整远程缓存。具体的网络读写
 * 通过受保护钩子留给子类：
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
 * 远程缓存的淘汰由服务端（如 Redis maxmemory-policy）管理，本地不挂载
 * {@link EvictionPolicy}（{@link #setEvictionPolicy} 为空操作），容量视为无上限。
 * 线程安全性取决于子类所用的客户端实现（主流 Redis 客户端的连接池都是线程安全的）。
 * </p>
 */
public abstract class RemoteCache extends AbstractCacheStore<String, String> {

    /** 永不过期标记：ttlMillis ≤ 0 表示写入永不过期的键 */
    protected static final long NO_EXPIRE = -1L;

    /** 命名空间前缀，可为空串；非空时所有 key 操作前自动拼接 */
    private final String namespace;

    protected RemoteCache() {
        this("");
    }

    protected RemoteCache(String namespace) {
        super(-1); // 远程淘汰由服务端管理，本地视为无上限
        this.namespace = namespace == null ? "" : namespace;
    }

    // ========== 留口子：子类用 Redis 客户端实现以下钩子 ==========

    /** 写入键值。{@code ttlMillis > 0} 时同时设置过期时间（毫秒）；{@code ttlMillis == NO_EXPIRE} 时写入永不过期的键。 */
    protected abstract void doSet(String key, String value, long ttlMillis);

    /** 读取键值，不存在返回 {@code null}。 */
    protected abstract String doGet(String key);

    /** 仅当 key 不存在时写入（SET NX），{@code ttlMillis} 语义同 {@link #doSet}；返回是否写入成功。 */
    protected abstract boolean doSetNx(String key, String value, long ttlMillis);

    /** 为已存在的 key 设置过期时间（毫秒）；返回是否设置成功（key 存在）。 */
    protected abstract boolean doExpire(String key, long ttlMillis);

    /** 查询 key 的剩余过期时间（毫秒）；key 不存在返回 -2，key 存在但永不过期返回 -1（与 Redis PTTL 语义一致）。 */
    protected abstract long doTtl(String key);

    /** 删除 key；返回是否删除了已存在的 key。 */
    protected abstract boolean doDelete(String key);

    /** key 是否存在（含已过期但未惰性删除的键，由后端语义决定）。 */
    protected abstract boolean doExists(String key);

    /** 当前键数量（如 Redis DBSIZE）。 */
    protected abstract long doSize();

    /** 清空全部键（如 Redis FLUSHDB）。 */
    protected abstract void doClear();

    // ========== 可覆盖的扩展点 ==========

    /** 拼接命名空间前缀。子类可覆盖以自定义 key 编码（如哈希、序列化）。 */
    protected String wrapKey(String key) {
        return namespace + key;
    }

    // ========== 淘汰策略插件：远程由服务端管理，本地为空操作 ==========

    @Override
    public void setEvictionPolicy(EvictionPolicy<String, String> policy) {
        // 远程缓存的淘汰在服务端进行，本地策略插件无意义，忽略。
    }

    // ========== 原始存储钩子 ==========

    @Override
    protected void rawPut(String key, String value) {
        doSet(wrapKey(key), value, NO_EXPIRE);
    }

    @Override
    protected void rawPut(String key, String value, Duration duration) {
        doSet(wrapKey(key), value, duration.toMillis());
    }

    @Override
    protected boolean rawPutIfAbsent(String key, String value) {
        return doSetNx(wrapKey(key), value, NO_EXPIRE);
    }

    @Override
    protected boolean rawPutIfAbsent(String key, String value, Duration duration) {
        return doSetNx(wrapKey(key), value, duration.toMillis());
    }

    @Override
    protected String rawGet(String key) {
        return doGet(wrapKey(key));
    }

    @Override
    protected String rawRemove(String key) {
        String value = doGet(wrapKey(key));
        doDelete(wrapKey(key));
        return value;
    }

    @Override
    protected boolean rawContains(String key) {
        return doExists(wrapKey(key));
    }

    @Override
    protected Duration rawTtl(String key) {
        long millis = doTtl(wrapKey(key));
        if (millis == -2L) return null;            // key 不存在
        if (millis < 0L) return Duration.ZERO;     // 永不过期
        return Duration.ofMillis(millis);
    }

    @Override
    protected void rawSetTtl(String key, Duration duration) {
        if (duration == null) return;
        if (duration.isZero() || duration.isNegative()) {
            rawRemove(key);
            return;
        }
        doExpire(wrapKey(key), duration.toMillis());
    }

    @Override
    protected void rawClear() {
        doClear();
    }

    @Override
    protected Iterable<String> rawKeys() {
        return Collections.emptySet(); // 远端键集合不在本地维护，gc 不做本地扫描
    }

    @Override
    protected boolean rawIsExpired(String key) {
        return false; // 过期由后端管理
    }

    @Override
    protected long rawCount() {
        return doSize();
    }
}
