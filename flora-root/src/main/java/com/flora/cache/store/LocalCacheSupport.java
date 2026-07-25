package com.flora.cache.store;

import com.flora.cache.BoundedCache;
import com.flora.cache.Cache;
import com.flora.cache.CacheEventType;
import com.flora.cache.CacheEventListener;
import com.flora.cache.EvictableCache;
import com.flora.cache.EvictionPolicy;
import com.flora.cache.ObservableCache;

import java.time.Duration;

/**
 * 本地有界缓存抽象基类：实现 {@link RawStore} 提供本地 KV/TTL 存储钩子，
 * 组合 {@link CacheEngine} 获得完整的缓存行为（读写、事件、可挂策略、容量约束）。
 * 子类（如 {@link MemoryCache}）只需实现 {@code rawXxx} 原始存储钩子。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public abstract class LocalCacheSupport<K, V>
        implements RawStore<K, V>, Cache<K, V>, ObservableCache<K, V>, EvictableCache<K, V>, BoundedCache<K, V> {

    private final CacheEngine<K, V> engine;

    protected LocalCacheSupport() {
        this(-1L);
    }

    /**
     * @param capacity 容量上限（{@code <=0} 表示无上限 / 不淘汰）
     */
    protected LocalCacheSupport(long capacity) {
        this.engine = new CacheEngine<>(this, capacity);
    }

    // ========== Cache ==========

    @Override
    public void put(K key, V value) {
        engine.put(key, value);
    }

    @Override
    public boolean putIfAbsent(K key, V value) {
        return engine.putIfAbsent(key, value);
    }

    @Override
    public void put(K key, V value, Duration duration) {
        engine.put(key, value, duration);
    }

    @Override
    public boolean putIfAbsent(K key, V value, Duration duration) {
        return engine.putIfAbsent(key, value, duration);
    }

    @Override
    public V get(K key) {
        return engine.get(key);
    }

    @Override
    public void setTtl(K key, Duration duration) {
        engine.setTtl(key, duration);
    }

    @Override
    public Duration ttl(K key) {
        return engine.ttl(key);
    }

    @Override
    public V remove(K key) {
        return engine.remove(key);
    }

    @Override
    public void clear() {
        engine.clear();
    }

    @Override
    public long approxCount() {
        return engine.approxCount();
    }

    @Override
    public boolean containsKey(K key) {
        return engine.containsKey(key);
    }

    // ========== ObservableCache ==========

    @Override
    public void addListener(CacheEventType type, CacheEventListener<? super K, ? super V> listener) {
        engine.addListener(type, listener);
    }

    @Override
    public void removeListener(CacheEventType type, CacheEventListener<? super K, ? super V> listener) {
        engine.removeListener(type, listener);
    }

    @Override
    public void removeListeners(CacheEventType type) {
        engine.removeListeners(type);
    }

    // ========== EvictableCache ==========

    @Override
    public void setEvictionPolicy(EvictionPolicy<K, V> policy) {
        engine.setEvictionPolicy(policy);
    }

    @Override
    public EvictionPolicy<K, V> evictionPolicy() {
        return engine.evictionPolicy();
    }

    // ========== BoundedCache ==========

    @Override
    public long cleanUp() {
        return engine.cleanUp();
    }

    @Override
    public boolean isFull() {
        return engine.isFull();
    }

    @Override
    public long capacity() {
        return engine.capacity();
    }
}
