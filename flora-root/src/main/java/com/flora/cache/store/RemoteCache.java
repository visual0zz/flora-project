package com.flora.cache.store;

import com.flora.cache.Cache;
import com.flora.cache.CacheEventType;
import com.flora.cache.CacheEventListener;
import com.flora.cache.ObservableCache;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 远程缓存（Redis 等）抽象基类，键与值均为 {@code String}，与 Redis 协议对应。
 * <p>
 * 直接承载缓存编排与事件派发，不再依赖独立的缓存引擎类。子类用具体 Redis 客户端实现
 * {@code doXxx} 钩子即可获得完整远程缓存。
 * <p>
 * 本类刻意精简：远端过期与淘汰由后端（如 Redis maxmemory-policy）管理，故不承载容量维度、
 * 淘汰策略与本地过期扫描。线程安全性取决于子类所用客户端。
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
        implements Cache<String, String>, ObservableCache<String, String> {

    /** 永不过期标记：ttlMillis ≤ 0 表示写入永不过期的键 */
    protected static final long NO_EXPIRE = -1L;

    /** 命名空间前缀，可为空串；非空时所有 key 操作前自动拼接 */
    private final String namespace;

    private final Map<CacheEventType, List<CacheEventListener<? super String, ? super String>>> listeners
            = new ConcurrentHashMap<>();

    protected RemoteCache() {
        this("");
    }

    /**
     * @param namespace 命名空间前缀，可为空串；非空时所有 key 操作前自动拼接
     */
    protected RemoteCache(String namespace) {
        this.namespace = namespace == null ? "" : namespace;
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

    // ========== 私有存储方法（调用 doXxx） ==========

    private void storePut(String key, String value) {
        doSet(wrapKey(key), value, NO_EXPIRE);
    }

    private void storePut(String key, String value, Duration duration) {
        doSet(wrapKey(key), value, toTtlMillis(duration));
    }

    private boolean storePutIfAbsent(String key, String value) {
        return doSetNx(wrapKey(key), value, NO_EXPIRE);
    }

    private boolean storePutIfAbsent(String key, String value, Duration duration) {
        return doSetNx(wrapKey(key), value, toTtlMillis(duration));
    }

    private String storeGet(String key) {
        return doGet(wrapKey(key));
    }

    private String storeRemove(String key) {
        String value = doGet(wrapKey(key));
        doDelete(wrapKey(key));
        return value;
    }

    private boolean storeContains(String key) {
        return doExists(wrapKey(key));
    }

    private Duration storeTtl(String key) {
        long millis = doTtl(wrapKey(key));
        if (millis == -2L) return Duration.ZERO;   // 不存在
        if (millis == -1L) return Duration.MAX;     // 永不过期
        if (millis <= 0L) return Duration.ZERO;     // 已过期
        return Duration.ofMillis(millis);
    }

    private void storeSetTtl(String key, Duration duration) {
        if (duration == null) return;
        doExpire(wrapKey(key), toTtlMillis(duration));
    }

    private void storeClear() {
        doClear();
    }

    private long storeCount() {
        return doSize();
    }

    // ========== 写入 ==========

    @Override
    public void put(String key, String value) {
        if (value == null) throw new NullPointerException("value must not be null");
        storePut(key, value);
        if (hasListeners(CacheEventType.INSERT)) fire(CacheEventType.INSERT, key, null, value);
        if (hasListeners(CacheEventType.MUTATE)) fire(CacheEventType.MUTATE, key, null, value);
    }

    @Override
    public boolean putIfAbsent(String key, String value) {
        if (value == null) throw new NullPointerException("value must not be null");
        boolean inserted = storePutIfAbsent(key, value);
        if (inserted) {
            if (hasListeners(CacheEventType.INSERT)) fire(CacheEventType.INSERT, key, null, value);
            if (hasListeners(CacheEventType.MUTATE)) fire(CacheEventType.MUTATE, key, null, value);
        }
        return inserted;
    }

    @Override
    public void put(String key, String value, Duration duration) {
        if (value == null) throw new NullPointerException("value must not be null");
        if (duration == null) {
            put(key, value);
            return;
        }
        if (duration.isZero() || duration.isNegative()) {
            expireKey(key); // 零/负时长 = 立即过期，走过期删除管线
            return;
        }
        storePut(key, value, duration);
        if (hasListeners(CacheEventType.INSERT)) fire(CacheEventType.INSERT, key, null, value);
        if (hasListeners(CacheEventType.MUTATE)) fire(CacheEventType.MUTATE, key, null, value);
    }

    @Override
    public boolean putIfAbsent(String key, String value, Duration duration) {
        if (value == null) throw new NullPointerException("value must not be null");
        if (duration == null) {
            return putIfAbsent(key, value);
        }
        if (duration.isZero() || duration.isNegative()) {
            return false;
        }
        boolean inserted = storePutIfAbsent(key, value, duration);
        if (inserted) {
            if (hasListeners(CacheEventType.INSERT)) fire(CacheEventType.INSERT, key, null, value);
            if (hasListeners(CacheEventType.MUTATE)) fire(CacheEventType.MUTATE, key, null, value);
        }
        return inserted;
    }

    // ========== 读取 ==========

    @Override
    public String get(String key) {
        return storeGet(key);
    }

    // ========== TTL 管理 ==========

    @Override
    public void setTtl(String key, Duration duration) {
        if (duration == null) return;
        if (duration.isZero() || duration.isNegative()) {
            expireKey(key); // 刷新成零/负时长 = 立即过期，走过期删除管线
            return;
        }
        // Duration.MAX 表示永不过期（移除 TTL）；仅对存活键操作，过期/缺失键静默忽略，避免复活
        if (storeContains(key)) {
            storeSetTtl(key, duration);
            if (hasListeners(CacheEventType.TOUCH) || hasListeners(CacheEventType.MUTATE)) {
                String cur = storeGet(key);
                if (hasListeners(CacheEventType.TOUCH)) fire(CacheEventType.TOUCH, key, cur, cur);
                if (hasListeners(CacheEventType.MUTATE)) fire(CacheEventType.MUTATE, key, cur, cur);
            }
        }
    }

    @Override
    public Duration ttl(String key) {
        return storeTtl(key);
    }

    // ========== 删除 ==========

    @Override
    public String remove(String key) {
        String old = storeRemove(key);
        if (old == null) return null;
        if (hasListeners(CacheEventType.REMOVE)) fire(CacheEventType.REMOVE, key, old, null);
        if (hasListeners(CacheEventType.INVALIDATE)) fire(CacheEventType.INVALIDATE, key, old, null);
        return old;
    }

    @Override
    public void clear() {
        storeClear();
        if (hasListeners(CacheEventType.CLEAR)) fire(CacheEventType.CLEAR, null, null, null);
    }

    @Override
    public long approxCount() {
        return storeCount();
    }

    @Override
    public boolean containsKey(String key) {
        return storeContains(key);
    }

    // ========== 显式过期（仅零/负 TTL 路径使用） ==========

    private void expireKey(String key) {
        String old = storeRemove(key);
        if (old == null) return;
        if (hasListeners(CacheEventType.EXPIRE)) fire(CacheEventType.EXPIRE, key, old, null);
        if (hasListeners(CacheEventType.INVALIDATE)) fire(CacheEventType.INVALIDATE, key, old, null);
    }

    // ========== 事件监听器 ==========

    @Override
    public void addListener(CacheEventType type, CacheEventListener<? super String, ? super String> listener) {
        if (type == null || listener == null) return;
        listeners.computeIfAbsent(type, _ -> new CopyOnWriteArrayList<>()).add(listener);
    }

    @Override
    public void removeListener(CacheEventType type, CacheEventListener<? super String, ? super String> listener) {
        if (type == null || listener == null) return;
        List<CacheEventListener<? super String, ? super String>> list = listeners.get(type);
        if (list != null) list.remove(listener);
    }

    @Override
    public void removeListeners(CacheEventType type) {
        if (type == null) return;
        listeners.remove(type);
    }

    private boolean hasListeners(CacheEventType type) {
        List<CacheEventListener<? super String, ? super String>> list = listeners.get(type);
        return list != null && !list.isEmpty();
    }

    /**
     * 触发事件。约定：实际存储操作已完成之后才调用本方法，故监听器异常不影响已提交的业务逻辑。
     * 异常隔离：单个监听器异常被就地吞掉并继续派发其余监听器。
     */
    private void fire(CacheEventType type, String key, String oldValue, String newValue) {
        List<CacheEventListener<? super String, ? super String>> list = listeners.get(type);
        if (list == null || list.isEmpty()) return;
        for (CacheEventListener<? super String, ? super String> l : list) {
            try {
                l.onEvent(type, key, oldValue, newValue);
            } catch (RuntimeException ignore) {
                // 监听器故障不应影响缓存主流程与同批次其他监听器
            }
        }
    }
}
