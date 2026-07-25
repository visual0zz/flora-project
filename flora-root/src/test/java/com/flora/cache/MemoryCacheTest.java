package com.flora.cache;

import com.flora.cache.eviction.LRUEvictionPolicy;
import com.flora.cache.store.MemoryCache;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证合并后的 MemoryCache（继承 BoundedCacheSupport，构造即自挂 W-TinyLFU + TTL）
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
        c.addListener(CacheEventType.REMOVE, (t, k, o, n) -> events.add(t));
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
    void ttlSemantics() throws InterruptedException {
        MemoryCache<Integer, Integer> c = new MemoryCache<>();
        // 不设置过期时间 → MAX
        c.put(1, 1);
        assertEquals(Duration.MAX, c.ttl(1));
        // 不存在 → ZERO
        assertEquals(Duration.ZERO, c.ttl(99));
        // 带 TTL → 返回正数剩余时长
        c.put(2, 2, Duration.ofMillis(1000));
        Duration remaining = c.ttl(2);
        assertTrue(remaining.toMillis() > 0 && remaining.toMillis() <= 1000, "remaining=" + remaining);
        // 已过期 → ZERO
        Thread.sleep(1100);
        assertEquals(Duration.ZERO, c.ttl(2));
        // setTtl(MAX) → 移除过期时间，返回 MAX
        c.put(3, 3, Duration.ofMillis(1000));
        c.setTtl(3, Duration.MAX);
        assertEquals(Duration.MAX, c.ttl(3));
        // setTtl(ZERO) → 立即过期，ttl 返回 ZERO 且 key 已不存在
        c.put(4, 4, Duration.ofMillis(1000));
        c.setTtl(4, Duration.ZERO);
        assertEquals(Duration.ZERO, c.ttl(4));
        assertFalse(c.containsKey(4));
    }

    @Test
    void setTtlDoesNotReviveExpiredKey() throws InterruptedException {
        MemoryCache<Integer, Integer> c = new MemoryCache<>();
        c.put(1, 1, Duration.ofMillis(50));
        Thread.sleep(90); // 已过期（逻辑删除），但物理仍在 map 中
        c.setTtl(1, Duration.ofMillis(1000)); // 不应复活过期键
        assertFalse(c.containsKey(1));
        assertEquals(Duration.ZERO, c.ttl(1));
        // 对完全不存在的键 setTtl 也是静默无操作
        c.setTtl(99, Duration.ofMillis(1000));
        assertEquals(Duration.ZERO, c.ttl(99));
    }

    @Test
    void ttlCleanUpFiresExpire() throws InterruptedException {
        MemoryCache<Integer, Integer> c = new MemoryCache<>(100);
        c.put(1, 1, Duration.ofMillis(50));
        Thread.sleep(90);
        List<CacheEventType> events = new ArrayList<>();
        c.addListener(CacheEventType.EXPIRE, (t, k, o, n) -> events.add(t));
        long n = c.cleanUp();
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
        c.addListener(CacheEventType.EVICT, (t, k, o, n) -> events.add(t));
        for (int i = 0; i < 30; i++) c.put(i, i);
        assertFalse(events.isEmpty(), "容量淘汰应触发 EVICT 事件");
    }

    @Test
    void removeExpiredKeyReturnsNull() throws InterruptedException {
        MemoryCache<Integer, Integer> c = new MemoryCache<>();
        c.put(1, 1, Duration.ofMillis(50));
        Thread.sleep(90); // 已过期（逻辑删除），物理仍在 map 中
        assertNull(c.remove(1)); // 与 get/containsKey 一致，过期键视为不存在，不返回旧值
        assertFalse(c.containsKey(1));
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

    /** 插件式架构冒烟：构造即自带 W-TinyLFU，且可自由替换为其它策略插件 */
    @Test
    void composableSmoke() {
        MemoryCache<String, String> store = new MemoryCache<>(10);
        assertNotNull(store.evictionPolicy(), "构造应自带 W-TinyLFU 策略");
        store.setEvictionPolicy(new LRUEvictionPolicy<>(10, store::approxCount));
        store.put("k", "v");
        assertEquals("v", store.get("k"));
        assertTrue(store.evictionPolicy() instanceof LRUEvictionPolicy);
    }
}
