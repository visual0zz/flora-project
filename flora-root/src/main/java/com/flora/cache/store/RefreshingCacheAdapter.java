package com.flora.cache.store;

import com.flora.cache.interfaces.BoundedCache;
import com.flora.cache.interfaces.Cache;
import com.flora.cache.interfaces.CacheEventListener;
import com.flora.cache.interfaces.EvictionPolicy;
import com.flora.cache.interfaces.MemoryCache;
import com.flora.common.SharedExecutors;
import com.flora.tag.ModuleEntry;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 异步刷新缓存适配器：读取时立即返回缓存值，同时在后台异步刷新。
 * <p>语义与 Caffeine 的 {@code refreshAfterWrite} 类似——调用 {@link #get(Object)} 时：
 * 命中且未超过刷新间隔，直接返回旧值；命中但超过刷新间隔，仍返回旧值，
 * 并在后台提交一次刷新任务（由 {@code loader} 重新计算并写回）；
 * 值缺失或已过期时，同步调用 {@code loader} 加载并写回（无旧值可返回）。
 * 刷新失败（loader 抛异常）时保留旧值，且不推进刷新计时，下次读取会再次尝试。</p>
 * <p>并发约束：同一 key 最多存在一个排队中的刷新任务（重复触发被合并）；
 * 同步加载不设锁，极端并发下可能出现多次加载，最终只写入一个值（由底层缓存保证）。</p>
 * <p>实现 {@link MemoryCache} 契约，其余存储/淘汰/TTL 能力全部委托给被装饰的底层缓存。</p>
 * <p><b>与可观测缓存的组合约定</b>：裸使用手动组合时，本适配器应包在
 * {@code CacheListenerAdapter} <b>外层</b>（{@code RefreshingCacheAdapter.of(CacheListenerAdapter.of(store), ...)}），
 * 使后台刷新写回经过可观测层、PUT 事件不被截断；若包在可观测层内层，刷新写回直接落存储，
 * 监听器将看不到刷新产生的写入。通过 {@code Caches.memory().refreshing(...)} 链式创建时，
 * 该顺序由 {@code Caches} 工厂固定保证。</p>
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
@ModuleEntry
public final class RefreshingCacheAdapter<K, V> implements MemoryCache<K, V> {

    private final Cache<K, V> delegate;
    private final Function<? super K, ? extends V> loader;
    private final Duration refreshAfter;
    /** key → 最近一次成功写入/刷新的时间戳(ms) */
    private final ConcurrentHashMap<K, Long> refreshedAt = new ConcurrentHashMap<>();
    /** key → 是否已有排队中的刷新任务（防止同 key 并发重复刷新） */
    private final ConcurrentHashMap<K, Boolean> refreshing = new ConcurrentHashMap<>();

    /**
     * 包装为异步刷新缓存。见 {@link #of(Cache, Duration, Function)}。
     * 包内可见，仅供 {@code of(...)} 工厂与 {@code Caches} 使用。
     */
    RefreshingCacheAdapter(Cache<K, V> delegate,
                           Duration refreshAfter,
                           Function<? super K, ? extends V> loader) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.loader = Objects.requireNonNull(loader, "loader must not be null");
        Objects.requireNonNull(refreshAfter, "refreshAfter must not be null");
        if (refreshAfter.isZero() || refreshAfter.isNegative()) {
            throw new IllegalArgumentException("refreshAfter 必须为正时长");
        }
        this.refreshAfter = refreshAfter;
    }

    /**
     * 包装为异步刷新缓存：读取立即返回旧值，超过 {@code refreshAfter} 后在后台用 {@code loader} 刷新。
     * 返回类型与 {@code delegate} 的接口层级一致（输入什么接口就输出什么接口）。
     * 后台刷新任务通过 {@link SharedExecutors} 的共享执行器执行。
     *
     * @param delegate     被装饰的缓存（承载实际存储、TTL 与淘汰）
     * @param refreshAfter 刷新间隔（正时长）
     * @param loader       重新计算缓存值的函数，用于后台刷新与缺失加载
     * @param <K>          键类型
     * @param <V>          值类型
     */
    public static <K, V> Cache<K, V> of(Cache<K, V> delegate,
                                        Duration refreshAfter,
                                        Function<? super K, ? extends V> loader) {
        return new RefreshingCacheAdapter<>(delegate, refreshAfter, loader);
    }

    /** 包装 {@link BoundedCache}，返回 {@link BoundedCache} 视图。见 {@link #of(Cache, Duration, Function)}。 */
    public static <K, V> BoundedCache<K, V> of(BoundedCache<K, V> delegate,
                                               Duration refreshAfter,
                                               Function<? super K, ? extends V> loader) {
        return new RefreshingCacheAdapter<>(delegate, refreshAfter, loader);
    }

    /** 包装 {@link MemoryCache}，返回 {@link MemoryCache} 视图。见 {@link #of(Cache, Duration, Function)}。 */
    public static <K, V> MemoryCache<K, V> of(MemoryCache<K, V> delegate,
                                              Duration refreshAfter,
                                              Function<? super K, ? extends V> loader) {
        return new RefreshingCacheAdapter<>(delegate, refreshAfter, loader);
    }

    // ========== 读取 ==========

    @Override
    public V get(K key) {
        V v = delegate.get(key);
        if (v == null) {
            return loadSynchronously(key);
        }
        maybeRefreshAsync(key);
        return v;
    }

    @Override
    public V get(K key, Duration ttl) {
        // 保留底层的滑动续期语义
        V v = delegate.get(key, ttl);
        if (v == null) {
            return loadSynchronously(key);
        }
        maybeRefreshAsync(key);
        return v;
    }

    // ========== 写入 ==========

    @Override
    public void put(K key, V value) {
        delegate.put(key, value);
        touched(key);
    }

    @Override
    public boolean putIfAbsent(K key, V value) {
        boolean inserted = delegate.putIfAbsent(key, value);
        if (inserted) touched(key);
        return inserted;
    }

    @Override
    public void put(K key, V value, Duration duration) {
        delegate.put(key, value, duration);
        touched(key);
    }

    @Override
    public boolean putIfAbsent(K key, V value, Duration duration) {
        boolean inserted = delegate.putIfAbsent(key, value, duration);
        if (inserted) touched(key);
        return inserted;
    }

    // ========== TTL 管理 ==========

    @Override
    public void setTtl(K key, Duration duration) {
        delegate.setTtl(key, duration);
    }

    @Override
    public Duration ttl(K key) {
        return delegate.ttl(key);
    }

    // ========== 删除 ==========

    @Override
    public V remove(K key) {
        refreshing.remove(key);
        refreshedAt.remove(key);
        return delegate.remove(key);
    }

    @Override
    public void clear() {
        refreshing.clear();
        refreshedAt.clear();
        delegate.clear();
    }

    // ========== 查询 ==========

    @Override
    public boolean containsKey(K key) {
        return delegate.containsKey(key);
    }

    @Override
    public long approxCount() {
        return delegate.approxCount();
    }

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mapping) {
        boolean existed = delegate.containsKey(key);
        V v = delegate.computeIfAbsent(key, mapping);
        if (!existed) touched(key);
        return v;
    }

    // ========== BoundedCache / MemoryCache 委托（按 delegate 实际层级降级） ==========

    @Override
    public long cleanUp() {
        return delegate instanceof BoundedCache<?, ?> bc ? bc.cleanUp() : 0L;
    }

    @Override
    public boolean isFull() {
        return delegate instanceof BoundedCache<?, ?> bc ? bc.isFull() : false;
    }

    @Override
    public long capacity() {
        return delegate instanceof BoundedCache<?, ?> bc ? bc.capacity() : 0L;
    }

    @Override
    public void setEvictionPolicy(EvictionPolicy<K, V> policy) {
        memoryCache().setEvictionPolicy(policy);
    }

    @Override
    public EvictionPolicy<K, V> evictionPolicy() {
        return memoryCache().evictionPolicy();
    }

    @Override
    public void setInternalRemovalListener(CacheEventListener<K, V> listener) {
        memoryCache().setInternalRemovalListener(listener);
    }

    @SuppressWarnings("unchecked")
    private MemoryCache<K, V> memoryCache() {
        if (delegate instanceof MemoryCache<?, ?>) {
            return (MemoryCache<K, V>) delegate;
        }
        throw new UnsupportedOperationException(
                "delegate is not a MemoryCache: " + delegate.getClass().getName());
    }

    // ========== 内部 ==========

    /** 缺失/过期：同步加载并写回，返回新值；loader 返回 null 或抛异常时返回 null 且不写入。 */
    private V loadSynchronously(K key) {
        try {
            V fresh = loader.apply(key);
            if (fresh == null) return null;
            delegate.put(key, fresh);
            touched(key);
            return fresh;
        } catch (RuntimeException e) {
            return null; // 同步加载失败视为未命中，由调用方自行决定降级
        }
    }

    /** 命中：超过刷新间隔时提交后台刷新（返回旧值由调用方返回）。 */
    private void maybeRefreshAsync(K key) {
        Long last = refreshedAt.get(key);
        if (last != null && (System.currentTimeMillis() - last) < refreshAfter.toMillis()) {
            return; // 未到刷新间隔
        }
        if (refreshing.putIfAbsent(key, Boolean.TRUE) != null) {
            return; // 已有排队中的刷新任务，合并本次触发
        }
        SharedExecutors.refresh().execute(() -> {
            try {
                V fresh = loader.apply(key);
                if (fresh != null) {
                    delegate.put(key, fresh);
                    touched(key);
                }
            } catch (RuntimeException e) {
                // 刷新失败保留旧值；不推进计时，下次读取继续触发
            } finally {
                refreshing.remove(key);
            }
        });
    }

    /** 记录一次成功写入/刷新，重置该 key 的刷新计时。 */
    private void touched(K key) {
        refreshedAt.put(key, System.currentTimeMillis());
    }
}
