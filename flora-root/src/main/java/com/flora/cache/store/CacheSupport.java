package com.flora.cache.store;

import com.flora.cache.CacheEventType;
import com.flora.cache.CacheEventListener;
import com.flora.cache.Cache;
import com.flora.cache.EvictionPolicy;
import com.flora.cache.RemovalCause;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 缓存抽象基类：实现 {@link Cache} 的通用读写、TTL、可选的淘汰策略回调与事件派发，
 * 供具体存储子类复用。子类只需实现一组 {@code rawXxx} 原始存储钩子（KV 与 TTL 的真正读写），
 * 其余逻辑由本类负责。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public abstract class CacheSupport<K, V> implements Cache<K, V> {

    private volatile EvictionPolicy<K, V> policy;

    private final Map<CacheEventType, List<CacheEventListener<? super K, ? super V>>> listeners
            = new ConcurrentHashMap<>();

    protected CacheSupport() {
    }

    // ========== 淘汰策略插件（字段仅在 CacheSupport 持有；公开挂载/卸载接口由 EvictableCache 实现类暴露） ==========

    /** 供子类（有界可淘汰缓存）暴露为 {@link com.flora.cache.EvictableCache} 的公开方法，普通基类不直接暴露。 */
    protected void setPolicy(EvictionPolicy<K, V> policy) {
        this.policy = policy;
    }

    /** 读取当前挂载的策略（可能为 {@code null}）。 */
    protected EvictionPolicy<K, V> policy() {
        return policy;
    }

    // ========== 写入 ==========

    @Override
    public void put(K key, V value) {
        if (value == null) throw new NullPointerException("value must not be null");
        if (rawContains(key)) {
            V old = rawGet(key);
            rawPut(key, value);
            onPut(key, true);
            onTouch(key, true);
            fire(CacheEventType.UPDATE, key, old, value);
            fire(CacheEventType.MUTATE, key, old, value);
        } else {
            ensureCapacity();
            rawPut(key, value);
            onPut(key, false);
            onTouch(key, false);
            fire(CacheEventType.INSERT, key, null, value);
            fire(CacheEventType.MUTATE, key, null, value);
        }
    }

    @Override
    public boolean putIfAbsent(K key, V value) {
        if (value == null) throw new NullPointerException("value must not be null");
        if (rawContains(key)) {
            onPut(key, true);
            onTouch(key, true);
            return false;
        }
        ensureCapacity();
        boolean inserted = rawPutIfAbsent(key, value);
            if (inserted) {
                onPut(key, false);
                onTouch(key, false);
                fire(CacheEventType.INSERT, key, null, value);
            fire(CacheEventType.MUTATE, key, null, value);
        } else {
            onPut(key, true);
            onTouch(key, true);
        }
        return inserted;
    }

    @Override
    public void put(K key, V value, Duration duration) {
        if (value == null) throw new NullPointerException("value must not be null");
        if (duration == null) {
            put(key, value);
            return;
        }
        if (duration.isZero() || duration.isNegative()) {
            expireKey(key);
            return;
        }
        if (rawContains(key)) {
            V old = rawGet(key);
            rawPut(key, value, duration);
            onPut(key, true);
            onTouch(key, true);
            fire(CacheEventType.UPDATE, key, old, value);
            fire(CacheEventType.MUTATE, key, old, value);
        } else {
            ensureCapacity();
            rawPut(key, value, duration);
            onPut(key, false);
            onTouch(key, false);
            fire(CacheEventType.INSERT, key, null, value);
            fire(CacheEventType.MUTATE, key, null, value);
        }
    }

    @Override
    public boolean putIfAbsent(K key, V value, Duration duration) {
        if (value == null) throw new NullPointerException("value must not be null");
        if (duration == null) {
            return putIfAbsent(key, value);
        }
        if (duration.isZero() || duration.isNegative()) {
            return false;
        }
        if (rawContains(key)) {
            onPut(key, true);
            onTouch(key, true);
            return false;
        }
        ensureCapacity();
        boolean inserted = rawPutIfAbsent(key, value, duration);
            if (inserted) {
                onPut(key, false);
                onTouch(key, false);
                fire(CacheEventType.INSERT, key, null, value);
            fire(CacheEventType.MUTATE, key, null, value);
        } else {
            onPut(key, true);
            onTouch(key, true);
        }
        return inserted;
    }

    // ========== 读取 ==========

    @Override
    public V get(K key) {
        V v = rawGet(key);
        if (v == null) {
            // 惰性过期：rawGet 已隐藏过期值（但不删除），此处把过期删除收归引擎管线，
            // 统一派发 EXPIRE 事件并通知策略，避免存储层私自删除导致策略索引残留幽灵条目。
            if (rawIsExpired(key)) expireKey(key);
            onGet(key, false);
            onTouch(key, false);
            return null;
        }
        onGet(key, true);
        onTouch(key, true);
        return v;
    }

    // ========== TTL 管理 ==========

    @Override
    public void setTtl(K key, Duration duration) {
        if (duration == null) return;
        if (duration.isZero() || duration.isNegative()) {
            expireKey(key); // 刷新成零/负时长 = 立即过期，走过期删除管线（而非显式删除）
            return;
        }
        if (rawContains(key)) {
            rawSetTtl(key, duration);
            onTouch(key, true); // TTL 刷新 = 重新确认条目仍被需要，刷新其淘汰热度
            V cur = rawGet(key);
            fire(CacheEventType.TOUCH, key, cur, cur);
            fire(CacheEventType.MUTATE, key, cur, cur);
        } else {
            rawSetTtl(key, duration);
        }
    }

    @Override
    public Duration ttl(K key) {
        return rawTtl(key);
    }

    // ========== 删除 ==========

    @Override
    public V remove(K key) {
        V old = rawRemove(key);
        if (old == null) return null;
        onRemove(key, RemovalCause.EXPLICIT);
        fire(CacheEventType.REMOVE, key, old, null);
        fire(CacheEventType.INVALIDATE, key, old, null);
        return old;
    }

    @Override
    public void clear() {
        rawClear();
        EvictionPolicy<K, V> p = policy;
        if (p != null) p.clear();
        fire(CacheEventType.CLEAR, null, null, null);
    }

    // ========== 查询 ==========

    @Override
    public long approxCount() {
        return rawCount();
    }

    @Override
    public boolean containsKey(K key) {
        return rawContains(key);
    }

    // ========== 内部：策略回调 ==========

    // 策略已挂载（policy != null）时即向策略喂数据；selectEvictVictim() 在容量未超限时返回 null，故不会触发删除。
    private void onPut(K key, boolean existed) {
        EvictionPolicy<K, V> p = policy;
        if (p != null) p.onPut(key, existed);
    }

    private void onGet(K key, boolean existed) {
        EvictionPolicy<K, V> p = policy;
        if (p != null) p.onGet(key, existed);
    }

    private void onTouch(K key, boolean existed) {
        EvictionPolicy<K, V> p = policy;
        if (p != null) p.onTouch(key, existed);
    }

    protected void onRemove(K key, RemovalCause cause) {
        EvictionPolicy<K, V> p = policy;
        if (p != null) p.onRemove(key, cause);
    }

    // ========== 内部：过期扫描 + 淘汰驱动 ==========

    /** 扫描并清理过期项（O(n)，仅在 cleanUp / ensureCapacity 时低频发生）。 */
    protected long sweepExpired() {
        long count = 0;
        for (K key : rawKeys()) {
            if (rawIsExpired(key) && expireKey(key)) count++;
        }
        return count;
    }

    /**
     * 把单个过期 key 走引擎删除管线：从存储移除 + 通知策略(EXPIRE) + 派发 EXPIRE/INVALIDATE 事件。
     * 返回是否真的删除了一个值（并发已删则返回 {@code false}）。
     * 惰性过期（{@link #get}）与主动扫描（{@link #sweepExpired}）共用此路径，保证删除语义唯一。
     */
    private boolean expireKey(K key) {
        V old = rawRemove(key);
        if (old == null) return false;
        onRemove(key, RemovalCause.EXPIRE);
        fire(CacheEventType.EXPIRE, key, old, null);
        fire(CacheEventType.INVALIDATE, key, old, null);
        return true;
    }

    /**
     * 条目集合即将增长前的钩子（写路径在插入新 key 之前调用），用于腾出容量、清理过期。
     * 默认空操作：无界缓存无需淘汰。有界缓存在 {@code BoundedCacheSupport} 中覆写此方法以驱动扫描过期 + 容量淘汰。
     * <p>
     * 命名强调「在会导致容量增长的写入之前」调用，而非「写入之后」，以明确其职责是提前腾位。
     */
    protected void ensureCapacity() {
    }

    // ========== 事件监听器 ==========

    // 以下三个为 concrete 方法，供子类继承复用。

    public void addListener(CacheEventType type, CacheEventListener<? super K, ? super V> listener) {
        if (type == null || listener == null) return;
        listeners.computeIfAbsent(type, _ -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public void removeListener(CacheEventType type, CacheEventListener<? super K, ? super V> listener) {
        if (type == null || listener == null) return;
        List<CacheEventListener<? super K, ? super V>> list = listeners.get(type);
        if (list != null) list.remove(listener);
    }

    public void removeListeners(CacheEventType type) {
        if (type == null) return;
        listeners.remove(type);
    }

    /**
     * 触发事件。约定：实际存储操作已完成之后才调用本方法，故监听器异常不影响已提交的业务逻辑。
     * 异常隔离：单个监听器异常被就地吞掉并继续派发其余监听器。
     */
    protected void fire(CacheEventType type, K key, V oldValue, V newValue) {
        List<CacheEventListener<? super K, ? super V>> list = listeners.get(type);
        if (list == null) return;
        for (CacheEventListener<? super K, ? super V> l : list) {
            try {
                l.onEvent(type, key, oldValue, newValue);
            } catch (RuntimeException ignore) {
                // 监听器故障不应影响缓存主流程与同批次其他监听器
            }
        }
    }

    // ========== 原始存储钩子（子类实现） ==========

    /** 覆盖写入（永不过期）。 */
    protected abstract void rawPut(K key, V value);

    /** 覆盖写入（带 TTL，duration 已保证为正数）。 */
    protected abstract void rawPut(K key, V value, Duration duration);

    /** 原子写入，返回是否写入成功（仅当 key 不存在）。 */
    protected abstract boolean rawPutIfAbsent(K key, V value);

    /** 原子写入（带 TTL，duration 已保证为正数），返回是否写入成功。 */
    protected abstract boolean rawPutIfAbsent(K key, V value, Duration duration);

    /** 读取值；不存在返回 {@code null}。 */
    protected abstract V rawGet(K key);

    /** 删除并返回旧值；不存在返回 {@code null}。 */
    protected abstract V rawRemove(K key);

    /** 是否存在且未过期（用于写时分支判断与 {@link #containsKey}）。 */
    protected abstract boolean rawContains(K key);

    /** 剩余过期时长；不存在返回 {@code null}，永不过期返回 {@link Duration#ZERO}。 */
    protected abstract Duration rawTtl(K key);

    /** 设置/更新过期时间（key 不存在由子类决定行为）。 */
    protected abstract void rawSetTtl(K key, Duration duration);

    /** 清空全部。 */
    protected abstract void rawClear();

    /** 所有 key 的快照（供 cleanUp 扫描）。 */
    protected abstract Iterable<K> rawKeys();

    /** 指定 key 是否已过期（未过期或不存在返回 {@code false}）。 */
    protected abstract boolean rawIsExpired(K key);

    /** 当前条目数量近似值。 */
    protected abstract long rawCount();
}
