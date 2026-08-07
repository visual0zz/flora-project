package com.flora.cache.store;

import com.flora.cache.Cache;

import java.util.Objects;

/**
 * 远程缓存（Redis 等）抽象基类，键与值均为 {@code String}，与 Redis 协议对应。
 * <p>
 * 子类用具体 Redis 客户端实现 {@code doXxx} 钩子即可获得完整远程缓存。本类刻意精简：
 * 远端过期与淘汰由后端（如 Redis maxmemory-policy）管理，故不承载容量维度、淘汰策略与本地过期扫描，
 * 也不区分 {@code INSERT}/{@code UPDATE}（每次 put 统一写入）。线程安全性取决于子类所用客户端。
 * <p>
 * 本类只实现最小契约 {@link Cache}，<b>不管理任何监听器</b>。如需事件监听，请用可观测装饰器
 * （{@code CacheListenerAdapter.of(this)}）包一层，调用方即可 {@code addListener} 订阅事件——
 * 这与本地缓存 {@link ConcurrentHashMapCache} 的做法完全一致，避免在每个缓存实现里重复事件代码。
 * 装饰器仅在公开 API 面（put / putIfAbsent / remove / clear / setTtl）上派发事件；
 * 远端自身的淘汰 / 过期由后端驱动，不经过本地装饰器，故不会派发对应事件。
 * （内部移除钩子 {@link MemoryCache#setInternalRemovalListener} 仅由本地托管的
 * {@link MemoryCache} 实现（如 {@link ConcurrentHashMapCache}）持有；本类不实现该接口，
 * 无法桥接远端驱动的 EVICT / EXPIRE，故其不可观测。）
 *
 * <pre>{@code
 * RemoteCache raw = new RemoteCache("myapp:") {
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
 * // 需要可观测时再装饰：
 * ObservableCache<String, String> cache = CacheListenerAdapter.of(raw);
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
public abstract class RemoteCache implements Cache<String, String> {

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

    // ========== 写入 ==========

    @Override
    public void put(String key, String value) {
        Objects.requireNonNull(value, "value must not be null");
        doSet(key, value, -1L);
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
        doSet(key, value, ttlMillis);
    }

    @Override
    public boolean putIfAbsent(String key, String value) {
        Objects.requireNonNull(value, "value must not be null");
        return doSetNx(key, value, -1L);
    }

    @Override
    public boolean putIfAbsent(String key, String value, java.time.Duration duration) {
        Objects.requireNonNull(value, "value must not be null");
        if (duration != null && (duration.isZero() || duration.isNegative())) return false;
        long ttlMillis = (duration == null || duration.equals(java.time.Duration.MAX)) ? -1L : duration.toMillis();
        return doSetNx(key, value, ttlMillis);
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
        return old;
    }

    @Override
    public void clear() {
        doClear();
    }

    @Override
    public long approxCount() {
        return doSize();
    }
}
