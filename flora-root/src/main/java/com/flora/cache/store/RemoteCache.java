package com.flora.cache.store;

import com.flora.cache.Cache;
import com.flora.cache.CacheEventType;
import com.flora.cache.CacheEventListener;
import com.flora.cache.ObservableCache;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 远程缓存（Redis 等）抽象基类，键与值均为 {@code String}，与 Redis 协议对应。
 * <p>
 * 子类用具体 Redis 客户端实现 {@code doXxx} 钩子即可获得完整远程缓存。本类刻意精简：
 * 远端过期与淘汰由后端（如 Redis maxmemory-policy）管理，故不承载容量维度、淘汰策略与本地过期扫描，
 * 也不区分 {@code INSERT}/{@code UPDATE}（每次 put 统一派发 INSERT，由后端语义决定覆盖行为）。
 * 线程安全性取决于子类所用客户端。
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

    /** 永不过期标记：ttlMillis == NO_EXPIRE 表示写入永不过期的键。 */
    protected static final long NO_EXPIRE = -1L;

    /** 命名空间前缀，可为空串；非空时所有 key 操作前自动拼接。 */
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

    // ========== 子类钩子（用具体 Redis 客户端实现） ==========

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

    // ========== 写入 ==========

    @Override
    public void put(String key, String value) {
        Objects.requireNonNull(value, "value must not be null");
        doSet(wrapKey(key), value, NO_EXPIRE);
        fireUpsert(key, value);
    }

    @Override
    public void put(String key, String value, Duration duration) {
        Objects.requireNonNull(value, "value must not be null");
        if (duration == null) {
            put(key, value);
            return;
        }
        if (duration.isZero() || duration.isNegative()) {
            expireKey(key); // 零/负时长 = 立即过期，走过期删除管线
            return;
        }
        doSet(wrapKey(key), value, toTtlMillis(duration));
        fireUpsert(key, value);
    }

    @Override
    public boolean putIfAbsent(String key, String value) {
        Objects.requireNonNull(value, "value must not be null");
        return doPutIfAbsent(key, value, null);
    }

    @Override
    public boolean putIfAbsent(String key, String value, Duration duration) {
        Objects.requireNonNull(value, "value must not be null");
        if (duration != null && (duration.isZero() || duration.isNegative())) return false;
        return doPutIfAbsent(key, value, duration);
    }

    private boolean doPutIfAbsent(String key, String value, Duration duration) {
        boolean inserted;
        inserted = duration == null ? doSetNx(wrapKey(key), value, NO_EXPIRE) : doSetNx(wrapKey(key), value, toTtlMillis(duration));
        if (inserted) fireUpsert(key, value);
        return inserted;
    }

    // ========== 读取 ==========

    @Override
    public String get(String key) {
        return doGet(wrapKey(key));
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
        if (!doExists(wrapKey(key))) return;
        doExpire(wrapKey(key), toTtlMillis(duration));
        if (hasListeners(CacheEventType.TOUCH) || hasListeners(CacheEventType.MUTATE)) {
            fireTouch(key, doGet(wrapKey(key)));
        }
    }

    @Override
    public Duration ttl(String key) {
        long millis = doTtl(wrapKey(key));
        if (millis == -2L) return Duration.ZERO;   // 不存在
        if (millis == -1L) return Duration.MAX;     // 永不过期
        if (millis <= 0L) return Duration.ZERO;     // 已过期
        return Duration.ofMillis(millis);
    }

    // ========== 删除 / 查询 ==========

    @Override
    public String remove(String key) {
        String value = doGet(wrapKey(key));
        doDelete(wrapKey(key));
        String old = value;
        if (old == null) return null;
        fireRemoval(key, old, CacheEventType.REMOVE);
        return old;
    }

    @Override
    public void clear() {
        doClear();
        if (hasListeners(CacheEventType.CLEAR)) fire(CacheEventType.CLEAR, null, null, null);
    }

    @Override
    public long approxCount() {
        return doSize();
    }

    @Override
    public boolean containsKey(String key) {
        return doExists(wrapKey(key));
    }

    // ========== 显式过期（仅零/负 TTL 路径使用） ==========

    private void expireKey(String key) {
        String value = doGet(wrapKey(key));
        doDelete(wrapKey(key));
        String old = value;
        if (old == null) return;
        fireRemoval(key, old, CacheEventType.EXPIRE);
    }

    // ========== 事件监听器（ObservableCache） ==========

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

    /** 写事件派发：INSERT 与聚合 MUTATE 一并触发；无监听器时不读取或派发。 */
    private void fireUpsert(String key, String value) {
        if (hasListeners(CacheEventType.INSERT)) fire(CacheEventType.INSERT, key, null, value);
        if (hasListeners(CacheEventType.MUTATE)) fire(CacheEventType.MUTATE, key, null, value);
    }

    /** TTL 刷新事件派发：TOUCH 与聚合 MUTATE 一并触发（值不变，old/new 均为当前值）。 */
    private void fireTouch(String key, String value) {
        if (hasListeners(CacheEventType.TOUCH)) fire(CacheEventType.TOUCH, key, value, value);
        if (hasListeners(CacheEventType.MUTATE)) fire(CacheEventType.MUTATE, key, value, value);
    }

    /** 失效事件派发：具体类型（REMOVE/EXPIRE）与聚合 INVALIDATE 一并触发。 */
    private void fireRemoval(String key, String old, CacheEventType primary) {
        if (hasListeners(primary)) fire(primary, key, old, null);
        if (hasListeners(CacheEventType.INVALIDATE)) fire(CacheEventType.INVALIDATE, key, old, null);
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
