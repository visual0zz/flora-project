package com.flora.cache.eviction;

import com.flora.cache.EvictionPolicy;
import com.flora.cache.RemovalCause;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

/**
 * LRU（最近最少使用）淘汰策略，key 基于、与存储无关。
 * <p>
 * 维护一把访问顺序的 {@link LinkedHashMap}：命中时把 key 移到 MRU 端，
 * 淘汰时取 LRU 端（最久未访问）的 key。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public final class LRUEvictionPolicy<K, V> implements EvictionPolicy<K, V> {

    private final LinkedHashMap<K, K> order = new LinkedHashMap<>(16, 0.75f, true);
    private final ReentrantLock lock = new ReentrantLock();
    private final long capacity;
    private final LongSupplier sizeOf;

    public LRUEvictionPolicy(long capacity, LongSupplier sizeOf) {
        this.capacity = capacity;
        this.sizeOf = sizeOf;
    }

    @Override
    public void onPut(K key, boolean existed) {
        lock.lock();
        try {
            order.put(key, key);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onGet(K key, boolean existed) {
        lock.lock();
        try {
            if (order.containsKey(key)) order.get(key); // 命中读取 → 移到 MRU
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onTouch(K key, boolean existed) {
        lock.lock();
        try {
            if (order.containsKey(key)) order.get(key); // 引用 → 移到 MRU（刷新最近使用）
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onRemove(K key, RemovalCause cause) {
        lock.lock();
        try {
            order.remove(key);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public K selectEvictVictim() {
        if (capacity <= 0 || sizeOf.getAsLong() < capacity) return null;
        lock.lock();
        try {
            Iterator<Map.Entry<K, K>> it = order.entrySet().iterator();
            if (!it.hasNext()) return null;
            K victim = it.next().getKey();
            it.remove();
            return victim;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void clear() {
        lock.lock();
        try {
            order.clear();
        } finally {
            lock.unlock();
        }
    }
}
