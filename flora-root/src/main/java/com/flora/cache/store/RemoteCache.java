package com.flora.cache.store;

import com.flora.cache.Cache;
import com.flora.cache.CacheEventType;
import com.flora.cache.CacheEventListener;
import com.flora.cache.ObservableCache;

import java.time.Duration;
import java.util.Collections;

/**
 * 远程缓存（Redis 等）抽象基类，键与值均为 {@code String}，与 Redis 协议对应。
 * <p>
 * 实现 {@link RawStore} 把网络读写落在子类实现的 {@code doXxx} 钩子，组合一个
 * {@code capacity<=0}、{@code policy=null} 的 {@link CacheEngine} 获得 put/get/remove 与事件派发能力。
 * 子类用具体 Redis 客户端实现 {@code doXxx} 钩子即可获得完整远程缓存。
 * <p>
 * 淘汰由服务端（如 Redis maxmemory-policy）管理，本地不暴露 {@link com.flora.cache.EvictableCache}
 * 能力、不持有容量维度。线程安全性取决于子类所用客户端。
 *
 * <pre>{@code
 * RemoteCache cache = new RemoteCache("myapp:") {
 *     protected void doSet(String key, String value, long ttlMillis) { jedis.set(key, value); }
 *     protected String doGet(String key)                          { return jedis.get(key); }
 *     protected boolean doSetNx(String key, String value, long ttl) { ... }
 *     protected boolean doExpire(String key, long ttl)            { ... }
 *     protected long doTtl(String key)                            { ... }
 *     protected boolean doDelete(String key)                      { ... }
 *     protected boolean doExists(String key)                      { ... }
 *     protected long doSize()                                     { ... }
 *     protected void doClear()                                    { ... }
 * };
 * }</pre>
 */
public abstract class RemoteCache
        implements RawStore<String, String>, Cache<String, String>, ObservableCache<String, String> {

    /** 永不过期标记：ttlMillis ≤ 0 表示写入永不过期的键 */
    protected static final long NO_EXPIRE = -1L;

    /** 命名空间前缀，可为空串；非空时所有 key 操作前自动拼接 */
    private final String namespace;

    private final CacheEngine<String, String> engine;

    protected RemoteCache() {
        this("");
    }

    /**
     * @param namespace 命名空间前缀，可为空串；非空时所有 key 操作前自动拼接
     */
    protected RemoteCache(String namespace) {
        this.namespace = namespace == null ? "" : namespace;
        // 远程淘汰由服务端管理，本地视为无上限、无策略
        this.engine = new CacheEngine<>(this, -1L);
    }

    // ========== 留口子：子类用 Redis 客户端实现以下钩子 ==========

    /** 写入键值。{@code ttlMillis > 0} 时同时设置过期时间（毫秒）；{@code ttlMillis == NO_EXPIRE} 时写入永不过期的键。 */
    protected abstract void doSet(String key, String value, long ttlMillis);

    /** 读取键值，不存在返回 {@code null}。 */
    protected abstract String doGet(String key);

    /** 仅当 key 不存在时写入（SET NX），{@code ttlMillis} 语义同 {@link #doSet}；返回是否写入成功。 */
    protected abstract boolean doSetNx(String key, String value, long ttlMillis);

    /** 为已存在的 key 设置过期时间（毫秒）；{@code ttlMillis > 0} 设置过期；{@code ttlMillis == NO_EXPIRE} 表示移除过期时间（PERSIST，永不过期）。返回是否设置成功（key 存在）。 */
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

    /** 将 {@link Duration} 转为后端 TTL 毫秒：{@code null} 或 {@link Duration#MAX} 表示永不过期（{@link #NO_EXPIRE}），其余按正数转毫秒。 */
    private static long toTtlMillis(Duration d) {
        return d == null || d.equals(Duration.MAX) ? NO_EXPIRE : d.toMillis();
    }

    // ========== Cache ==========

    @Override
    public void put(String key, String value) {
        engine.put(key, value);
    }

    @Override
    public boolean putIfAbsent(String key, String value) {
        return engine.putIfAbsent(key, value);
    }

    @Override
    public void put(String key, String value, Duration duration) {
        engine.put(key, value, duration);
    }

    @Override
    public boolean putIfAbsent(String key, String value, Duration duration) {
        return engine.putIfAbsent(key, value, duration);
    }

    @Override
    public String get(String key) {
        return engine.get(key);
    }

    @Override
    public void setTtl(String key, Duration duration) {
        engine.setTtl(key, duration);
    }

    @Override
    public Duration ttl(String key) {
        return engine.ttl(key);
    }

    @Override
    public String remove(String key) {
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
    public boolean containsKey(String key) {
        return engine.containsKey(key);
    }

    // ========== ObservableCache ==========

    @Override
    public void addListener(CacheEventType type, CacheEventListener<? super String, ? super String> listener) {
        engine.addListener(type, listener);
    }

    @Override
    public void removeListener(CacheEventType type, CacheEventListener<? super String, ? super String> listener) {
        engine.removeListener(type, listener);
    }

    @Override
    public void removeListeners(CacheEventType type) {
        engine.removeListeners(type);
    }

    // ========== 原始存储钩子（实现 RawStore） ==========

    @Override
    public void rawPut(String key, String value) {
        doSet(wrapKey(key), value, NO_EXPIRE);
    }

    @Override
    public void rawPut(String key, String value, Duration duration) {
        doSet(wrapKey(key), value, toTtlMillis(duration));
    }

    @Override
    public boolean rawPutIfAbsent(String key, String value) {
        return doSetNx(wrapKey(key), value, NO_EXPIRE);
    }

    @Override
    public boolean rawPutIfAbsent(String key, String value, Duration duration) {
        return doSetNx(wrapKey(key), value, toTtlMillis(duration));
    }

    @Override
    public String rawGet(String key) {
        return doGet(wrapKey(key));
    }

    @Override
    public String rawRemove(String key) {
        String value = doGet(wrapKey(key));
        doDelete(wrapKey(key));
        return value;
    }

    @Override
    public boolean rawContains(String key) {
        return doExists(wrapKey(key));
    }

    @Override
    public Duration rawTtl(String key) {
        long millis = doTtl(wrapKey(key));
        if (millis == -2L) return Duration.ZERO;   // 不存在
        if (millis == -1L) return Duration.MAX;     // 永不过期
        if (millis <= 0L) return Duration.ZERO;     // 已过期
        return Duration.ofMillis(millis);
    }

    @Override
    public void rawSetTtl(String key, Duration duration) {
        if (duration == null) return;
        // duration 已非 ZERO/非 NEGATIVE；MAX 表示永不过期（移除 TTL，PERSIST）
        doExpire(wrapKey(key), toTtlMillis(duration));
    }

    @Override
    public void rawClear() {
        doClear();
    }

    @Override
    public Iterable<String> rawKeys() {
        return Collections.emptySet(); // 远端键集合不在本地维护，cleanUp 不做本地扫描
    }

    @Override
    public boolean rawIsExpired(String key) {
        return false; // 过期由后端管理
    }

    @Override
    public long rawCount() {
        return doSize();
    }
}
