package com.flora.cache.eviction;

import com.flora.cache.EvictionPolicy;
import com.flora.cache.RemovalCause;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * LFU（最不经常使用）淘汰策略，key 基于、与存储无关。
 * <p>
 * 为每个 key 维护访问计数与插入序号：命中时计数 +1；淘汰时取
 * 计数最小者，计数相同取最早写入者。O(1) 更新，淘汰为 O(n) 扫描
 * （仅发生在超限时，由挂载它的缓存循环调用 {@code selectEvictVictim()} 驱动）。
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
    public void onPut(K key, boolean existed) {
        long s = seq.incrementAndGet();
        freq.compute(key, (_, v) -> {
            if (v == null) return new long[]{1, s};
            v[0] = Math.max(v[0], 1);
            v[1] = s;
            return v;
        });
    }

    @Override
    public void onGet(K key, boolean existed) {
        // LFU 无独立的读取专属逻辑：读取的热度累加由 onTouch 统一承担（未命中键不在索引中，自然无操作）
    }

    @Override
    public void onTouch(K key, boolean existed) {
        freq.computeIfPresent(key, (_, v) -> {
            v[0]++;
            v[1] = seq.incrementAndGet();
            return v;
        });
    }

    @Override
    public void onRemove(K key, RemovalCause cause) {
        freq.remove(key);
    }

    @Override
    public K selectEvictVictim() {
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
    public void clear() {
        freq.clear();
    }
}
