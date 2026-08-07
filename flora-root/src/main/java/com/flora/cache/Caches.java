package com.flora.cache;

import com.flora.cache.store.ConcurrentHashMapCache;
import com.flora.tag.ModuleEntry;

/**
 * 缓存构造工厂。通过导出 API 暴露具体实现的创建，
 * 使其他模块无需依赖 {@code com.flora.cache.store} 等内部子包。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
@ModuleEntry
public final class Caches {

    private Caches() {
    }

    /** 无界内存缓存：不驱逐，适合长期驻留的条目（如已加载的原生库句柄）。 */
    public static <K, V> MemoryCache<K, V> memory() {
        return new ConcurrentHashMapCache<>(-1);
    }

    /** 有界内存缓存，默认 W-TinyLFU 驱逐策略。 */
    public static <K, V> MemoryCache<K, V> memory(long capacity) {
        return new ConcurrentHashMapCache<>(capacity);
    }

    /** 有界内存缓存，使用自定义驱逐策略。 */
    public static <K, V> MemoryCache<K, V> memory(long capacity, EvictionPolicy<K, V> policy) {
        return new ConcurrentHashMapCache<>(capacity, policy);
    }
}
