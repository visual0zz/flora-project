package com.flora.cache;

import com.flora.cache.store.ComposedCacheStore;
import com.flora.cache.store.ConcurrentHashMapStore;
import com.flora.cache.store.MemoryCache;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证重构后的 MemoryCache（= ConcurrentHashMapStore + WTinyLfuEvictionPolicy 组合）
 * 行为正确：基础读写、TTL 过期、事件、容量边界、putIfAbsent、无界模式。
 */
class MemoryCacheTest {

    @Test
    void putAndGet() {
        MemoryCache<String, String> c = new MemoryCache<>();
        c.put("a", "1");
        assertEquals("1", c.get("a"));
        assertNull(c.get("missing"));
    }

    @Test
    void removeFiresRemoveEvent() {
        MemoryCache<Integer, Integer> c = new MemoryCache<>();
        c.put(1, 10);
        List<CacheEventType> events = new ArrayList<>();
        c.addListener(CacheEventType.REMOVE, (t, k, v) -> events.add(t));
        c.remove(1);
        assertFalse(c.containsKey(1));
        assertEquals(List.of(CacheEventType.REMOVE), events);
    }

    @Test
    void ttlLazyExpiry() throws InterruptedException {
        MemoryCache<Integer, Integer> c = new MemoryCache<>(100);
        c.put(1, 1, Duration.ofMillis(50));
        assertEquals(1, c.get(1));
        Thread.sleep(90);
        assertNull(c.get(1)); // 惰性过期
        assertFalse(c.containsKey(1));
    }

    @Test
    void ttlGcFiresExpire() throws InterruptedException {
        MemoryCache<Integer, Integer> c = new MemoryCache<>(100);
        c.put(1, 1, Duration.ofMillis(50));
        Thread.sleep(90);
        List<CacheEventType> events = new ArrayList<>();
        c.addListener(CacheEventType.EXPIRE, (t, k, v) -> events.add(t));
        long n = c.gc();
        assertEquals(1, n);
        assertEquals(List.of(CacheEventType.EXPIRE), events);
        assertFalse(c.containsKey(1));
    }

    @Test
    void capacityBounded() {
        int cap = 10;
        MemoryCache<Integer, Integer> c = new MemoryCache<>(cap);
        for (int i = 0; i < 50; i++) c.put(i, i);
        // 容量软上限 + W-TinyLFU 窗口松弛，size 略高于 cap 但远小于插入量
        assertTrue(c.approxCount() <= cap + 2, "size=" + c.approxCount());
        assertTrue(c.approxCount() < 50, "应有条目被淘汰");
        // 所有仍在缓存中的条目值与 key 一致
        for (int i = 0; i < 50; i++) {
            Integer v = c.get(i);
            if (v != null) assertEquals(i, v);
        }
    }

    @Test
    void unboundedKeepsAll() {
        MemoryCache<Integer, Integer> c = new MemoryCache<>(); // 无界
        for (int i = 0; i < 100; i++) c.put(i, i);
        assertEquals(100, c.approxCount());
        for (int i = 0; i < 100; i++) assertEquals(i, c.get(i));
    }

    @Test
    void putIfAbsent() {
        MemoryCache<Integer, Integer> c = new MemoryCache<>();
        assertTrue(c.putIfAbsent(1, 10));
        assertFalse(c.putIfAbsent(1, 20)); // 已存在，不覆盖
        assertEquals(10, c.get(1));
    }

    @Test
    void evictFiresEvictEvent() {
        int cap = 5;
        MemoryCache<Integer, Integer> c = new MemoryCache<>(cap);
        List<CacheEventType> events = new ArrayList<>();
        c.addListener(CacheEventType.EVICT, (t, k, v) -> events.add(t));
        for (int i = 0; i < 30; i++) c.put(i, i);
        assertFalse(events.isEmpty(), "容量淘汰应触发 EVICT 事件");
    }

    @Test
    void clearEmpties() {
        MemoryCache<Integer, Integer> c = new MemoryCache<>();
        c.put(1, 1);
        c.put(2, 2);
        c.clear();
        assertEquals(0, c.approxCount());
        assertFalse(c.containsKey(1));
    }

    /** 组合式架构冒烟：任意 CacheStore + 任意 EvictionPolicy 可自由装配 */
    @Test
    void composableSmoke() {
        ConcurrentHashMapStore<String, String> store = new ConcurrentHashMapStore<>();
        ComposedCacheStore<String, String> cache = new ComposedCacheStore<>(
                store, new com.flora.cache.eviction.WTinyLfuEvictionPolicy<>(10, store::approxCount), 10);
        cache.put("k", "v");
        assertEquals("v", cache.get("k"));
    }
}
