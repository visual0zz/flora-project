package com.flora.cache;

import com.flora.cache.Cache;
import com.flora.cache.eviction.FIFOEvictionPolicy;
import com.flora.cache.eviction.LFUEvictionPolicy;
import com.flora.cache.eviction.LRUEvictionPolicy;
import com.flora.cache.store.MemoryCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 LRU / LFU / FIFO 三种淘汰策略作为插件挂载到 MemoryCache 后产生确定性淘汰顺序。
 */
class EvictionPolicyTest {

    private static <K, V> MemoryCache<K, V> cache(int cap, java.util.function.Function<MemoryCache<K, V>, EvictionPolicy<K, V>> policyFn) {
        MemoryCache<K, V> store = new MemoryCache<>(cap);
        store.setEvictionPolicy(policyFn.apply(store));
        return store;
    }

    @Test
    void lruEvictsLeastRecentlyUsed() {
        Cache<Integer, Integer> c = cache(3, s -> new LRUEvictionPolicy<Integer, Integer>(3, s::approxCount));
        c.put(1, 1);
        c.put(2, 2);
        c.put(3, 3);
        c.get(1); // 1 变热，最近最少使用的是 2
        c.put(4, 4); // 触发淘汰
        assertTrue(c.containsKey(1));
        assertTrue(c.containsKey(3));
        assertTrue(c.containsKey(4));
        assertFalse(c.containsKey(2), "LRU 应淘汰最久未访问的 2");
        assertEquals(3, c.approxCount());
    }

    @Test
    void fifoEvictsOldestInserted() {
        Cache<Integer, Integer> c = cache(3, s -> new FIFOEvictionPolicy<Integer, Integer>(3, s::approxCount));
        c.put(1, 1);
        c.put(2, 2);
        c.put(3, 3);
        c.get(1); // FIFO 忽略访问
        c.put(4, 4);
        assertFalse(c.containsKey(1), "FIFO 应淘汰最早写入的 1");
        assertTrue(c.containsKey(2));
        assertTrue(c.containsKey(3));
        assertTrue(c.containsKey(4));
        assertEquals(3, c.approxCount());
    }

    @Test
    void lfuEvictsLeastFrequentlyUsed() {
        Cache<Integer, Integer> c = cache(3, s -> new LFUEvictionPolicy<Integer, Integer>(3, s::approxCount));
        c.put(1, 1);
        c.put(2, 2);
        c.put(3, 3);
        c.get(1);
        c.get(1); // 1: 3 次
        c.get(2); // 2: 2 次；3: 1 次
        c.put(4, 4);
        assertFalse(c.containsKey(3), "LFU 应淘汰访问最少(计数为1)的 3");
        assertTrue(c.containsKey(1));
        assertTrue(c.containsKey(2));
        assertTrue(c.containsKey(4));
        assertEquals(3, c.approxCount());
    }

    @Test
    void unboundedPolicyKeepsAll() {
        Cache<Integer, Integer> c = cache(-1, s -> new LRUEvictionPolicy<Integer, Integer>(-1, s::approxCount));
        for (int i = 0; i < 20; i++) c.put(i, i);
        assertEquals(20, c.approxCount());
    }
}
