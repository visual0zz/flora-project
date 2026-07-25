package com.flora.cache.eviction;

import com.flora.cache.EvictionPolicy;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

/**
 * FIFO（先进先出）淘汰策略，key 基于、与存储无关。
 * <p>
 * 维护插入顺序的 {@link LinkedHashMap}：新条目入队尾，淘汰时取队首
 * （最早写入）的 key。读取或命中不改变其顺序。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public final class FIFOEvictionPolicy<K, V> implements EvictionPolicy<K, V> {

    private final LinkedHashMap<K, K> queue = new LinkedHashMap<>(16, 0.75f, false);
    private final ReentrantLock lock = new ReentrantLock();
    private final long capacity;
    private final LongSupplier sizeOf;

    public FIFOEvictionPolicy(long capacity, LongSupplier sizeOf) {
        this.capacity = capacity;
        this.sizeOf = sizeOf;
    }

    @Override
    public void onPut(K key, boolean existed) {
        lock.lock();
        try {
            queue.put(key, key);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onGet(K key, boolean existed) {
        // 读取不改变顺序：顺序仅由写入决定
    }

    @Override
    public void onTouch(K key, boolean existed) {
        // 热度刷新不影响顺序：顺序仅由写入决定
    }

    @Override
    public void onRemove(K key) {
        lock.lock();
        try {
            queue.remove(key);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public K selectEvictVictim() {
        if (capacity <= 0 || sizeOf.getAsLong() < capacity) return null;
        lock.lock();
        try {
            Iterator<Map.Entry<K, K>> it = queue.entrySet().iterator();
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
            queue.clear();
        } finally {
            lock.unlock();
        }
    }
}
