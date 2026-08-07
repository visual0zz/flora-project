package com.flora.cache;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CacheTest {

    @Test
    void factoryCreatesUnboundedMemoryCache() {
        MemoryCache<String, String> cache = Caches.memory();
        assertNotNull(cache);
        assertEquals(0, cache.approxCount());
    }

    @Test
    void computeIfAbsentCachesAndComputesOnce() {
        Cache<String, String> cache = Caches.memory();
        AtomicInteger calls = new AtomicInteger();
        String v1 = cache.computeIfAbsent("k", k -> {
            calls.incrementAndGet();
            return "v";
        });
        String v2 = cache.computeIfAbsent("k", k -> {
            calls.incrementAndGet();
            return "other";
        });
        assertEquals("v", v1);
        assertEquals("v", v2, "已存在时不重算，返回原值");
        assertEquals(1, calls.get(), "mapping 只应执行一次");
        assertTrue(cache.containsKey("k"));
    }

    @Test
    void computeIfAbsentSkipsNullMapping() {
        Cache<String, String> cache = Caches.memory();
        String v = cache.computeIfAbsent("k", k -> null);
        assertNull(v);
        assertFalse(cache.containsKey("k"), "mapping 返回 null 时不写入");
    }
}
