package com.flora.cache;

import com.flora.cache.store.CacheListenerAdapter;
import com.flora.cache.store.ConcurrentHashMapCache;
import com.flora.tag.ModuleEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * 缓存构造工厂。通过导出 API 暴露具体实现的创建，
 * 使其他模块无需依赖 {@code com.flora.cache.store} 等内部子包。
 *
 * <p>链式构造器：{@link #memory()} 返回 {@link InMemoryCacheBuilder}，
 * 可依次设定容量、淘汰策略、可观测性，最后以 {@code get()} 收尾，
 * 例如 {@code Caches.<K, V>memory().capacity(12).evict(policy).get()}。
 * 调用 {@code observable()} 即把缓存包装为可观测视图（支持监听器，
 * EVICT / EXPIRE 经内部移除钩子桥接派发）。
 * <p>
 * 由于 Java 对链式泛型方法的目标类型推断在字段初始化器中不可靠，
 * 入口 {@link #memory()} 通常需显式类型见证 {@code Caches.<K, V>memory()}。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
@ModuleEntry
public final class Caches {

    private Caches() {
    }

    /** 启动一个内存缓存的链式构造；默认无界、不驱逐、不可观测。 */
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
            if (!observable) {
                return cache;
            }
            ObservableMemoryCache<K, V> obs = CacheListenerAdapter.of(cache);
            for (ListenerBinding<K, V> b : bindings) {
                obs.addListener(b.type(), b.listener());
            }
            return obs;
        }

        /** 监听器绑定（事件类型 + 监听器），供 {@link #get()} 统一注册。 */
        private record ListenerBinding<K, V>(CacheEventType type,
                                             CacheEventListener<? super K, ? super V> listener) {
        }
    }
}
