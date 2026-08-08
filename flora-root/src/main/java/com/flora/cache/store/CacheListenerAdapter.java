package com.flora.cache.store;

import com.flora.cache.interfaces.BoundedCache;
import com.flora.cache.interfaces.Cache;
import com.flora.cache.CacheEventType;
import com.flora.cache.interfaces.CacheEventListener;
import com.flora.cache.interfaces.EvictionPolicy;
import com.flora.cache.interfaces.MemoryCache;
import com.flora.cache.interfaces.ObservableBoundedCache;
import com.flora.cache.interfaces.ObservableCache;
import com.flora.cache.interfaces.ObservableMemoryCache;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 可观测缓存装饰器：包装一个 {@link Cache}/{@link BoundedCache}/{@link MemoryCache}，
 * 在保留其全部读写语义的同时，叠加事件监听能力。
 * <p>
 * 装饰器本身实现 {@link ObservableMemoryCache}/{@link ObservableBoundedCache}/
 * {@link ObservableCache}，通过 {@code of(...)} 工厂按被包装类型返回最具体的可观测视图：
 * <pre>{@code
 * ObservableMemoryCache<K,V> obs = CacheListenerAdapter.of(new ConcurrentHashMapCache<K,V>(1024));
 * obs.addListener(CacheEventType.PUT, (type, key, oldV, newV) -> ...);
 * }</pre>
 * 事件在装饰器拦截到的<b>显式</b>操作上派发（put / putIfAbsent / get / ttl / containsKey / setTtl / remove / clear）；
 * 其中 {@code GET} 在每次读取时都会派发，高频读场景需留意监听器开销。
 * 被包装缓存<b>内部</b>触发的淘汰与过期（{@code EVICT} / {@code EXPIRE}，如 {@code cleanUp()} 驱动的批量回收）
 * 经由 {@link MemoryCache#setInternalRemovalListener} 安装的内部移除钩子桥接派发：{@code of(...)} 包装时
 * 自动注入该钩子，把存储引擎的 EVICT / EXPIRE 转派给用户监听器，从而无需让存储直接持有事件总线。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public class CacheListenerAdapter<K, V>
        implements ObservableMemoryCache<K, V>, ObservableBoundedCache<K, V>, ObservableCache<K, V> {

    /** 被包装的底层缓存；所有读写操作委托给它。 */
    private final Cache<K, V> delegate;

    private final Map<CacheEventType, List<CacheEventListener<? super K, ? super V>>> listeners
            = new ConcurrentHashMap<>();

    /** 装饰器模式：包装一个已有缓存，使其可观测。包内可见，仅供 {@code of(...)} 工厂方法使用。 */
    CacheListenerAdapter(Cache<K, V> delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    // ========== 工厂方法 ==========

    public static <K, V> ObservableMemoryCache<K, V> of(MemoryCache<K, V> cache) {
        CacheListenerAdapter<K, V> a = new CacheListenerAdapter<>(cache);
        cache.setInternalRemovalListener(a::bridgeRemoval);
        return a;
    }

    public static <K, V> ObservableBoundedCache<K, V> of(BoundedCache<K, V> cache) {
        CacheListenerAdapter<K, V> a = new CacheListenerAdapter<>(cache);
        if (cache instanceof MemoryCache<K, V> mc) mc.setInternalRemovalListener(a::bridgeRemoval);
        return a;
    }

    public static <K, V> ObservableCache<K, V> of(Cache<K, V> cache) {
        CacheListenerAdapter<K, V> a = new CacheListenerAdapter<>(cache);
        if (cache instanceof MemoryCache<K, V> mc) mc.setInternalRemovalListener(a::bridgeRemoval);
        return a;
    }

    /** 内部移除桥接：把存储引擎的 EVICT/EXPIRE 转派给用户监听器。 */
    private void bridgeRemoval(CacheEventType type, K key, V oldValue, V newValue) {
        fire(type, key, oldValue, newValue);
    }

    // ========== 监听器管理（ObservableCache） ==========

    @Override
    public void addListener(CacheEventType type, CacheEventListener<? super K, ? super V> listener) {
        if (type == null || listener == null) return;
        listeners.computeIfAbsent(type, _ -> new CopyOnWriteArrayList<>()).add(listener);
    }

    @Override
    public void removeListener(CacheEventType type, CacheEventListener<? super K, ? super V> listener) {
        if (type == null || listener == null) return;
        List<CacheEventListener<? super K, ? super V>> list = listeners.get(type);
        if (list != null) list.remove(listener);
    }

    @Override
    public void removeListeners(CacheEventType type) {
        if (type == null) return;
        listeners.remove(type);
    }

    /** 是否存在关注该类型的监听器（包内可见，供本类与复用引擎的缓存调用）。 */
    boolean hasListeners(CacheEventType type) {
        List<CacheEventListener<? super K, ? super V>> list = listeners.get(type);
        return list != null && !list.isEmpty();
    }

    /**
     * 派发事件。约定在底层存储操作完成后调用；监听器异常被就地吞掉，不影响主流程与同批次其他监听器。
     * {@code oldValue}/{@code newValue} 由各调用点仅在确有监听器时才求值传入。
     */
    void fire(CacheEventType type, K key, V oldValue, V newValue) {
        List<CacheEventListener<? super K, ? super V>> list = listeners.get(type);
        if (list == null || list.isEmpty()) return;
        for (CacheEventListener<? super K, ? super V> l : list) {
            try {
                l.onEvent(type, key, oldValue, newValue);
            } catch (RuntimeException ignore) {
                // todo 等log部分代码最终定型调试好之后，这里要记录异常
            }
        }
    }

    // ========== 写入（委托 + 派发） ==========

    @Override
    public void put(K key, V value) {
        Objects.requireNonNull(value, "value must not be null");
        delegate.put(key, value);
        if (hasListeners(CacheEventType.PUT)) fire(CacheEventType.PUT, key, null, value);
    }

    @Override
    public void put(K key, V value, Duration duration) {
        Objects.requireNonNull(value, "value must not be null");
        if (duration == null) {
            put(key, value);
            return;
        }
        if (duration.isZero() || duration.isNegative()) {
            remove(key); // 零/负时长 = 立即失效
            return;
        }
        delegate.put(key, value, duration);
        if (hasListeners(CacheEventType.PUT)) fire(CacheEventType.PUT, key, null, value);
    }

    @Override
    public boolean putIfAbsent(K key, V value) {
        Objects.requireNonNull(value, "value must not be null");
        boolean inserted = delegate.putIfAbsent(key, value);
        if (inserted && hasListeners(CacheEventType.PUT_IF_ABSENT))
            fire(CacheEventType.PUT_IF_ABSENT, key, null, value);
        return inserted;
    }

    @Override
    public boolean putIfAbsent(K key, V value, Duration duration) {
        Objects.requireNonNull(value, "value must not be null");
        if (duration != null && (duration.isZero() || duration.isNegative())) return false;
        boolean inserted = delegate.putIfAbsent(key, value, duration);
        if (inserted && hasListeners(CacheEventType.PUT_IF_ABSENT))
            fire(CacheEventType.PUT_IF_ABSENT, key, null, value);
        return inserted;
    }

    // ========== 读取（纯委托） ==========

    @Override
    public V get(K key) {
        return get(key, null);
    }

    @Override
    public V get(K key, Duration ttl) {
        V v = delegate.get(key, ttl);
        if (hasListeners(CacheEventType.GET)) fire(CacheEventType.GET, key, null, v);
        return v;
    }

    @Override
    public boolean containsKey(K key) {
        boolean c = delegate.containsKey(key);
        if (hasListeners(CacheEventType.CONTAINS)) fire(CacheEventType.CONTAINS, key, null, null);
        return c;
    }

    // ========== TTL 管理 ==========

    @Override
    public void setTtl(K key, Duration duration) {
        if (duration == null) return;
        if (duration.isZero() || duration.isNegative()) {
            remove(key);
            return;
        }
        if (!delegate.containsKey(key)) return; // 不复活缺失/过期键
        delegate.setTtl(key, duration);
        if (hasListeners(CacheEventType.SET_TTL)) fire(CacheEventType.SET_TTL, key, null, null);
    }

    @Override
    public Duration ttl(K key) {
        Duration d = delegate.ttl(key);
        if (hasListeners(CacheEventType.GET_TTL)) fire(CacheEventType.GET_TTL, key, null, null);
        return d;
    }

    // ========== 删除（委托 + 派发） ==========

    @Override
    public V remove(K key) {
        if (!delegate.containsKey(key)) return null; // 不关心旧值且不存在则无操作
        V old = delegate.get(key);
        V removed = delegate.remove(key);
        if (removed != null && hasListeners(CacheEventType.REMOVE))
            fire(CacheEventType.REMOVE, key, old, null);
        return removed;
    }

    @Override
    public void clear() {
        delegate.clear();
        if (hasListeners(CacheEventType.CLEAR)) fire(CacheEventType.CLEAR, null, null, null);
    }

    @Override
    public long approxCount() {
        return delegate.approxCount();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    // ========== 容量约束（BoundedCache，委托给底层） ==========

    @Override
    public long cleanUp() {
        if (delegate instanceof BoundedCache<?, ?>) {
            BoundedCache<K, V> bc = (BoundedCache<K, V>) delegate;
            return bc.cleanUp();
        }
        return 0L;
    }

    @Override
    public boolean isFull() {
        if (delegate instanceof BoundedCache<?, ?>) {
            BoundedCache<K, V> bc = (BoundedCache<K, V>) delegate;
            return bc.isFull();
        }
        return false;
    }

    @Override
    public long capacity() {
        if (delegate instanceof BoundedCache<?, ?>) {
            BoundedCache<K, V> bc = (BoundedCache<K, V>) delegate;
            return bc.capacity();
        }
        return 0L;
    }

    // ========== 淘汰策略（MemoryCache，委托给底层） ==========

    @Override
    public void setEvictionPolicy(EvictionPolicy<K, V> policy) {
        if (delegate instanceof MemoryCache<?, ?>) {
            MemoryCache<K, V> mc = (MemoryCache<K, V>) delegate;
            mc.setEvictionPolicy(policy);
            return;
        }
        throw new UnsupportedOperationException("delegate is not a MemoryCache: " + delegate.getClass().getName());
    }

    @Override
    public EvictionPolicy<K, V> evictionPolicy() {
        if (delegate instanceof MemoryCache<?, ?>) {
            MemoryCache<K, V> mc = (MemoryCache<K, V>) delegate;
            return mc.evictionPolicy();
        }
        throw new UnsupportedOperationException("delegate is not a MemoryCache: " + delegate.getClass().getName());
    }
}
