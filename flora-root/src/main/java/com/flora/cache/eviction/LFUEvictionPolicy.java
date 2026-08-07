package com.flora.cache.eviction;

import com.flora.cache.CacheEventType;
import com.flora.cache.EvictionPolicy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * LFU（最不经常使用）淘汰策略，key 基于、与存储无关。
 * <p>
 * 为每个 key 维护访问计数与插入序号：命中时计数 +1；淘汰时取
 * 计数最小者，计数相同取最早写入者。
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public final class LFUEvictionPolicy<K, V> implements EvictionPolicy<K, V> {

    /** [0]=访问计数, [1]=插入/访问序号（用于计数相同时的稳定 tie-break） */
    private final ConcurrentHashMap<K, long[]> freq = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong();
    private final long capacity;
    private final LongSupplier sizeOf;

    public LFUEvictionPolicy(long capacity, LongSupplier sizeOf) {
        this.capacity = capacity;
        this.sizeOf = sizeOf;
    }

    @Override
    public void onAccess(K key, CacheEventType action, boolean existed, V oldValue, V newValue) {
        switch (action) {
            case PUT -> {
                long s = seq.incrementAndGet();
                freq.compute(key, (_, v) -> {
                    if (v == null) return new long[]{1, s};
                    v[0] = Math.max(v[0], 1);
                    v[1] = s;
                    return v;
                });
            }
            case GET, SET_TTL -> {
                if (!existed) return; // 未命中 / 不存在：不在索引中，自然无操作
                freq.computeIfPresent(key, (_, v) -> {
                    v[0]++;
                    v[1] = seq.incrementAndGet();
                    return v;
                });
            }
        }
    }

    @Override
    public void onRemove(K key, V oldValue, CacheEventType reason) {
        freq.remove(key);
    }

    @Override
    public K selectVictim() {
        if (capacity <= 0 || sizeOf.getAsLong() < capacity) return null;
        K best = null;
        long bestF = Long.MAX_VALUE;
        long bestS = Long.MAX_VALUE;
        for (Map.Entry<K, long[]> e : freq.entrySet()) {
            long f = e.getValue()[0];
            long s = e.getValue()[1];
            if (f < bestF || (f == bestF && s < bestS)) {
                bestF = f;
                bestS = s;
                best = e.getKey();
            }
        }
        if (best != null) freq.remove(best);
        return best;
    }

    @Override
    public void onClear() {
        freq.clear();
    }
}
