package com.flora.cache.store;

import com.flora.cache.CacheEventType;
import com.flora.cache.CacheEventListener;
import com.flora.cache.Cache;
import com.flora.cache.EvictionPolicy;

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

    // ========== 淘汰策略插件（concrete 方法，供子类继承复用） ==========

    public void setEvictionPolicy(EvictionPolicy<K, V> policy) {
        this.policy = policy;
    }

    public EvictionPolicy<K, V> evictionPolicy() {
        return policy;
    }

    // ========== 写入 ==========

    @Override
    public void put(K key, V value) {
        if (value == null) throw new NullPointerException("value must not be null");
        if (rawContains(key)) {
            rawPut(key, value);
            onPut(key);
            onTouch(key);
            fire(CacheEventType.UPDATE, key, value);
            fire(CacheEventType.MUTATE, key, value);
        } else {
            afterWrite();
            rawPut(key, value);
            onPut(key);
            onTouch(key);
            fire(CacheEventType.INSERT, key, value);
            fire(CacheEventType.MUTATE, key, value);
        }
    }

    @Override
    public boolean putIfAbsent(K key, V value) {
        if (value == null) throw new NullPointerException("value must not be null");
        if (rawContains(key)) {
            onPut(key);
            onTouch(key);
            return false;
        }
        afterWrite();
        boolean inserted = rawPutIfAbsent(key, value);
            if (inserted) {
                onPut(key);
                onTouch(key);
                fire(CacheEventType.INSERT, key, value);
            fire(CacheEventType.MUTATE, key, value);
        } else {
            onPut(key);
            onTouch(key);
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
            remove(key);
            return;
        }
        if (rawContains(key)) {
            rawPut(key, value, duration);
            onPut(key);
            onTouch(key);
            fire(CacheEventType.UPDATE, key, value);
            fire(CacheEventType.MUTATE, key, value);
        } else {
            afterWrite();
            rawPut(key, value, duration);
            onPut(key);
            onTouch(key);
            fire(CacheEventType.INSERT, key, value);
            fire(CacheEventType.MUTATE, key, value);
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
            onPut(key);
            onTouch(key);
            return false;
        }
        afterWrite();
        boolean inserted = rawPutIfAbsent(key, value, duration);
            if (inserted) {
                onPut(key);
                onTouch(key);
                fire(CacheEventType.INSERT, key, value);
            fire(CacheEventType.MUTATE, key, value);
        } else {
            onPut(key);
            onTouch(key);
        }
        return inserted;
    }

    // ========== 读取 ==========

    @Override
    public V get(K key) {
        V v = rawGet(key);
        onGet(key);
        onTouch(key);
        return v;
    }

    // ========== TTL 管理 ==========

    @Override
    public void setTtl(K key, Duration duration) {
        if (duration == null) return;
        if (duration.isZero() || duration.isNegative()) {
            remove(key); // 刷新成零/负时长等价于删除
            return;
        }
        if (rawContains(key)) {
            rawSetTtl(key, duration);
            fire(CacheEventType.TOUCH, key, rawGet(key));
            fire(CacheEventType.MUTATE, key, rawGet(key));
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
        onRemove(key);
        fire(CacheEventType.REMOVE, key, old);
        fire(CacheEventType.INVALIDATE, key, old);
        return old;
    }

    @Override
    public void clear() {
        rawClear();
        EvictionPolicy<K, V> p = policy;
        if (p != null) p.clear();
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
    private void onPut(K key) {
        EvictionPolicy<K, V> p = policy;
        if (p != null) p.onPut(key);
    }

    private void onGet(K key) {
        EvictionPolicy<K, V> p = policy;
        if (p != null) p.onGet(key);
    }

    private void onTouch(K key) {
        EvictionPolicy<K, V> p = policy;
        if (p != null) p.onTouch(key);
    }

    protected void onRemove(K key) {
        EvictionPolicy<K, V> p = policy;
        if (p != null) p.onRemove(key);
    }

    // ========== 内部：过期扫描 + 淘汰驱动 ==========

    /** 扫描并清理过期项（O(n)，仅在 gc / afterWrite 时低频发生）。 */
    protected long sweepExpired() {
        long count = 0;
        for (K key : rawKeys()) {
            if (!rawIsExpired(key)) continue;
            V old = rawRemove(key);
            if (old != null) {
                onRemove(key);
                fire(CacheEventType.EXPIRE, key, old);
                fire(CacheEventType.INVALIDATE, key, old);
                count++;
            }
        }
        return count;
    }

    /**
     * 写入后的钩子，由子类按需覆写。默认空操作：无界缓存写入后无需触发淘汰。
     * 有界缓存在 {@code BoundedCacheSupport} 中覆写此方法以驱动容量淘汰。
     */
    protected void afterWrite() {
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
    protected void fire(CacheEventType type, K key, V value) {
        List<CacheEventListener<? super K, ? super V>> list = listeners.get(type);
        if (list == null) return;
        for (CacheEventListener<? super K, ? super V> l : list) {
            try {
                l.onEvent(type, key, value);
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

    /** 所有 key 的快照（供 gc 扫描）。 */
    protected abstract Iterable<K> rawKeys();

    /** 指定 key 是否已过期（未过期或不存在返回 {@code false}）。 */
    protected abstract boolean rawIsExpired(K key);

    /** 当前条目数量近似值。 */
    protected abstract long rawCount();
}
