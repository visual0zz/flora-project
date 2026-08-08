package com.flora.cache;

import com.flora.cache.interfaces.Cache;
import com.flora.cache.interfaces.CacheEventListener;
import com.flora.cache.interfaces.EvictionPolicy;
import com.flora.cache.interfaces.MemoryCache;
import com.flora.cache.interfaces.ObservableCache;
import com.flora.cache.interfaces.ObservableMemoryCache;
import com.flora.common.RemoteTtlKVStore;
import com.flora.cache.impl.CacheListenerAdapter;
import com.flora.cache.impl.ConcurrentHashMapCache;
import com.flora.cache.impl.RefreshingCacheAdapter;
import com.flora.cache.impl.RemoteCache;
import com.flora.tag.ModuleEntry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * 缓存构造工厂。通过导出 API 暴露具体实现的创建，
 * 使其他模块无需依赖 {@code com.flora.cache.impl} 等内部子包。
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

    /** 启动一个远程缓存的链式构造（键值均为 {@code String}）。 */
    public static RemoteCacheBuilder remote() {
        return new RemoteCacheBuilder();
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
         * 后台刷新任务通过 {@link com.flora.common.SharedExecutors} 的共享执行器执行。
         * 见 {@link RefreshingCacheAdapter}。
         *
         * @param refreshAfter 刷新间隔（正时长）
         * @param loader       重新计算缓存值的函数（后台刷新与缺失加载）
         */
        public InMemoryCacheBuilder<K, V> refreshing(Duration refreshAfter,
                                                     Function<? super K, ? extends V> loader) {
            this.loader = loader;
            this.refreshAfter = refreshAfter;
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
            // 包装顺序固定：先 observable（若有）包底层，再 refreshing（若有）包外层——
            // refreshing 后台写回必经 observable 派发事件，不被截断；链式调用顺序不影响结果
            if (loader != null) {
                cache = RefreshingCacheAdapter.of(cache, refreshAfter, loader);
            }
            return cache;
        }
    }

    /** 监听器绑定（事件类型 + 监听器），供各构造器统一注册。 */
    private record ListenerBinding<K, V>(CacheEventType type,
                                         CacheEventListener<? super K, ? super V> listener) {
    }

    /**
     * 远程缓存链式构造器。需先 {@link #store(RemoteTtlKVStore)} 指定后端，终端 {@link #get()} 产出缓存实例。
     */
    public static final class RemoteCacheBuilder {

        private RemoteTtlKVStore store;
        private boolean observable;
        private final List<ListenerBinding<String, String>> bindings = new ArrayList<>();

        /** 指定远程存储后端。 */
        public RemoteCacheBuilder store(RemoteTtlKVStore store) {
            this.store = store;
            return this;
        }

        /** 包装为可观测缓存，使其支持监听器。 */
        public RemoteCacheBuilder observable() {
            this.observable = true;
            return this;
        }

        /** 注册一个监听器（监听全部事件类型），隐含 {@link #observable()}。 */
        public RemoteCacheBuilder listener(CacheEventListener<? super String, ? super String> listener) {
            this.observable = true;
            for (CacheEventType type : CacheEventType.values()) {
                bindings.add(new ListenerBinding<>(type, listener));
            }
            return this;
        }

        /** 注册指定类型的监听器，隐含 {@link #observable()}。 */
        public RemoteCacheBuilder listener(CacheEventType type,
                                           CacheEventListener<? super String, ? super String> listener) {
            this.observable = true;
            bindings.add(new ListenerBinding<>(type, listener));
            return this;
        }

        /** 完成构造，返回缓存实例；{@code observable} 时为可观测视图。 */
        public Cache<String, String> get() {
            Objects.requireNonNull(store, "store 未设置，请先调用 store(RemoteTtlKVStore)");
            Cache<String, String> cache = RemoteCache.of(store);
            if (observable) {
                ObservableCache<String, String> obs = CacheListenerAdapter.of(cache);
                for (ListenerBinding<String, String> b : bindings) {
                    obs.addListener(b.type(), b.listener());
                }
                cache = obs;
            }
            return cache;
        }
    }
}
