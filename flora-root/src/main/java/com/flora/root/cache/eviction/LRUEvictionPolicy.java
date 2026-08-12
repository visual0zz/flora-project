package com.flora.root.cache.eviction;

import com.flora.root.cache.CacheEventType;
import com.flora.root.cache.interfaces.EvictionPolicy;

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
    public void onAccess(K key, CacheEventType action, boolean existed, V oldValue, V newValue) {
        switch (action) {
            case PUT -> {
                lock.lock();
                try {
                    order.put(key, key); // 新建或覆盖都置 MRU 端
                } finally {
                    lock.unlock();
                }
            }
            case GET, SET_TTL -> {
                if (!existed) return;
                lock.lock();
                try {
                    if (order.containsKey(key)) order.get(key); // 命中 / 刷新 → 移到 MRU
                } finally {
                    lock.unlock();
                }
            }
        }
    }

    @Override
    public void onRemove(K key, V oldValue, CacheEventType reason) {
        lock.lock();
        try {
            order.remove(key);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public K selectVictim() {
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
    public void onClear() {
        lock.lock();
        try {
            order.clear();
        } finally {
            lock.unlock();
        }
    }
}
