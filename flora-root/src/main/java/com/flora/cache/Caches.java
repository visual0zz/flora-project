package com.flora.cache;

import com.flora.cache.store.CacheListenerAdapter;
import com.flora.cache.store.ConcurrentHashMapCache;
import com.flora.tag.ModuleEntry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * 缓存构造工厂。通过导出 API 暴露具体实现的创建，
 * 使其他模块无需依赖 {@code com.flora.cache.store} 等内部子包。
 *
 * <p>链式构造器：{@link #memory()} 返回 {@link InMemoryCacheBuilder}，
 * 可依次设定容量、淘汰策略、可观测性、异步刷新，最后以 {@code get()} 收尾，
 * 例如 {@code Caches.<K, V>memory().capacity(12).evict(policy).get()}。
 * 调用 {@code observable()} 即把缓存包装为可观测视图（支持监听器，
 * EVICT / EXPIRE 经内部移除钩子桥接派发）；调用 {@code refreshing(...)}
 * 即包装为异步刷新缓存（读取返回旧值、后台刷新）。
 * <p>
 * 由于 Java 对链式泛型方法的目标类型推断在字段初始化器中不可靠，
 * 入口 {@link #memory()} 通常需显式类型见证 {@code Caches.<K, V>memory()}。
 */
@ModuleEntry
public final class Caches {

    private Caches() {
    }

    /** 启动一个内存缓存的链式构造；默认无界、不驱逐、不可观测、不刷新。 */
    public static <K, V> InMemoryCacheBuilder<K, V> memory() {
        return new InMemoryCacheBuilder<>();
    }

    /**
     * 内存缓存链式构造器。每一步返回自身以便链式调用，终端 {@link #get()} 产出缓存实例。
     *
     * @param <K> 键类型
     * @param <V> 值类型
     */
    public static final class InMemoryCacheBuilder<K, V> {

        private long capacity = -1;
        private EvictionPolicy<K, V> policy;
        private boolean observable;
        private Function<? super K, ? extends V> loader;
        private Duration refreshAfter;
        private Executor executor;
        private final List<ListenerBinding<K, V>> bindings = new ArrayList<>();

        /** 设置容量上限；{@code <= 0} 表示无界。 */
        public InMemoryCacheBuilder<K, V> capacity(long capacity) {
            this.capacity = capacity;
            return this;
        }

        /** 设置自定义淘汰策略；不设置则使用默认 W-TinyLFU。 */
        public InMemoryCacheBuilder<K, V> evict(EvictionPolicy<K, V> policy) {
            this.policy = policy;
            return this;
        }

        /**
         * 启用异步刷新：读取立即返回旧值，超过 {@code refreshAfter} 后在后台用 {@code loader} 刷新。
         * 见 {@link RefreshingCacheAdapter}。
         *
         * @param loader       重新计算缓存值的函数（后台刷新与缺失加载）
         * @param refreshAfter 刷新间隔（正时长）
         * @param executor     执行后台刷新任务的线程池（生命周期由调用方管理）
         */
        public InMemoryCacheBuilder<K, V> refreshing(Function<? super K, ? extends V> loader,
                                                     Duration refreshAfter,
                                                     Executor executor) {
            this.loader = loader;
            this.refreshAfter = refreshAfter;
            this.executor = executor;
            return this;
        }

        /** 包装为可观测缓存，使其支持监听器（EVICT / EXPIRE 经内部移除钩子桥接派发）。 */
        public InMemoryCacheBuilder<K, V> observable() {
            this.observable = true;
            return this;
        }

        /** 注册一个监听器（监听全部事件类型），隐含 {@link #observable()}。 */
        public InMemoryCacheBuilder<K, V> listener(CacheEventListener<? super K, ? super V> listener) {
            this.observable = true;
            for (CacheEventType type : CacheEventType.values()) {
                bindings.add(new ListenerBinding<>(type, listener));
            }
            return this;
        }

        /** 注册指定类型的监听器，隐含 {@link #observable()}。 */
        public InMemoryCacheBuilder<K, V> listener(CacheEventType type,
                                                  CacheEventListener<? super K, ? super V> listener) {
            this.observable = true;
            bindings.add(new ListenerBinding<>(type, listener));
            return this;
        }

        /** 完成构造，返回缓存实例；{@code observable} 时为可观测视图（其本身也是 {@link MemoryCache}）。 */
        public MemoryCache<K, V> get() {
            MemoryCache<K, V> cache = (policy == null)
                    ? new ConcurrentHashMapCache<>(capacity)
                    : new ConcurrentHashMapCache<>(capacity, policy);
            if (observable) {
                ObservableMemoryCache<K, V> obs = CacheListenerAdapter.of(cache);
                for (ListenerBinding<K, V> b : bindings) {
                    obs.addListener(b.type(), b.listener());
                }
                cache = obs;
            }
            // refreshing 包在最外层：其后台刷新写回经 observable 派发事件，不被截断
            if (loader != null) {
                cache = RefreshingCacheAdapter.of(cache, loader, refreshAfter, executor);
            }
            return cache;
        }

        /** 监听器绑定（事件类型 + 监听器），供 {@link InMemoryCacheBuilder#get()} 统一注册。 */
        private record ListenerBinding<K, V>(CacheEventType type,
                                             CacheEventListener<? super K, ? super V> listener) {
        }
    }
}
