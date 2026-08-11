package com.flora.cache.impl;

import com.flora.cache.CacheEventType;
import com.flora.cache.Caches;
import com.flora.cache.interfaces.BoundedCache;
import com.flora.cache.interfaces.Cache;
import com.flora.cache.interfaces.MemoryCache;
import com.flora.cache.interfaces.ObservableMemoryCache;
import com.flora.cache.impl.CacheListenerAdapter;
import com.flora.cache.impl.RefreshingCacheAdapter;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class RefreshingCacheAdapterTest {

    private static MemoryCache<String, String> refreshingCache(
            Duration refreshAfter, Function<String, String> loader) {
        return Caches.<String, String>memory().refreshing(refreshAfter, loader).get();
    }

    /** 轮询等待 loader 调用次数达到期望值（后台刷新异步执行）。 */
    private static void awaitCalls(AtomicInteger calls, int expected) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (calls.get() < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
        assertEquals(expected, calls.get(), "loader 调用次数未达预期");
    }

    @Test
    void getReturnsStaleValueAndRefreshesInBackground() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        MemoryCache<String, String> cache = refreshingCache(
                Duration.ofMillis(100), k -> "v" + calls.incrementAndGet());

        cache.put("k", "initial");
        // 未超间隔：返回旧值，不触发刷新（put 后立即 get，远小于 100ms 间隔）
        assertEquals("initial", cache.get("k"));
        Thread.sleep(50);
        assertEquals(0, calls.get(), "未到刷新间隔不应触发刷新");

        Thread.sleep(100); // 累计超 100ms 刷新间隔
        // 超过间隔：仍返回旧值，但后台已提交刷新
        assertEquals("initial", cache.get("k"), "刷新完成前仍返回旧值");
        awaitCalls(calls, 1);
        assertEquals("v1", cache.get("k"), "刷新后值已更新");
    }

    @Test
    void missingKeyLoadsSynchronously() {
        AtomicInteger calls = new AtomicInteger();
        MemoryCache<String, String> cache = refreshingCache(
                Duration.ofMinutes(1), k -> {
                    calls.incrementAndGet();
                    return "loaded";
                });

        assertEquals("loaded", cache.get("missing"));
        assertTrue(cache.containsKey("missing"), "同步加载应写入缓存");
        assertEquals(1, calls.get(), "缺失加载应同步执行一次 loader");
    }

    @Test
    void refreshFailureKeepsStaleValueAndRetries() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        MemoryCache<String, String> cache = refreshingCache(Duration.ofMillis(1), k -> {
            if (calls.incrementAndGet() == 1) throw new IllegalStateException("boom");
            return "fresh";
        });

        cache.put("k", "old");
        Thread.sleep(5);
        assertEquals("old", cache.get("k"), "刷新前返回旧值"); // 触发第一次刷新（失败）
        awaitCalls(calls, 1); // 第一次刷新抛异常，旧值保留
        assertEquals("old", cache.get("k"), "刷新失败保留旧值"); // 再读触发第二次刷新

        // 失败不推进计时 → 第二次刷新成功写入新值
        awaitCalls(calls, 2);
        assertEquals("fresh", cache.get("k"), "刷新成功后值已更新");
    }

    @Test
    void putResetsRefreshTimer() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        MemoryCache<String, String> cache = refreshingCache(
                Duration.ofMillis(100), k -> {
                    calls.incrementAndGet();
                    return "new";
                });

        cache.put("k", "old");
        Thread.sleep(150); // 超过 100ms 刷新间隔
        cache.put("k", "updated"); // 手动写回应重置计时
        assertEquals("updated", cache.get("k")); // put 后立即 get，未到间隔
        Thread.sleep(50);
        assertEquals(0, calls.get(), "put 重置计时后未到间隔不应触发刷新");
    }

    @Test
    void concurrentGetsSubmitSingleRefresh() throws Exception {
        // loader 首次调用时阻塞，确保所有并发 get 都在刷新完成前发生（验证刷新合并）
        CountDownLatch loaderGate = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        MemoryCache<String, String> cache = refreshingCache(Duration.ofMillis(1), k -> {
            calls.incrementAndGet();
            try {
                loaderGate.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "fresh";
        });

        cache.put("k", "old");
        Thread.sleep(5); // 超过刷新间隔

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    assertEquals("old", cache.get("k"), "并发读取均应返回旧值");
                });
            }
            start.countDown();
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS), "并发读取未在限定时间内完成");
        }
        // 所有并发 get 已返回旧值；释放首次刷新，验证并发期间只提交了一次刷新
        loaderGate.countDown();
        awaitCalls(calls, 1);
        long deadline = System.currentTimeMillis() + 5000;
        while (!"fresh".equals(cache.get("k")) && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
        assertEquals("fresh", cache.get("k"), "刷新后值已更新");
    }

    @Test
    void refreshWriteTriggersPutEventWhenObservable() throws Exception {
        // 验证组合顺序：refreshing 外层、observable 内层，后台刷新写回应派发 PUT 事件
        List<String> events = new CopyOnWriteArrayList<>();
        MemoryCache<String, String> cache = Caches.<String, String>memory()
                .refreshing(Duration.ofMillis(1), k -> "fresh")
                .listener(CacheEventType.PUT, (type, key, oldV, newV) -> events.add("PUT:" + key + "=" + newV))
                .get();

        cache.put("k", "old");
        Thread.sleep(5);
        assertEquals("old", cache.get("k"), "刷新前返回旧值");
        long deadline = System.currentTimeMillis() + 5000;
        while (events.stream().noneMatch(e -> e.equals("PUT:k=fresh"))
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
        assertTrue(events.stream().anyMatch(e -> e.equals("PUT:k=fresh")),
                "后台刷新写回应派发 PUT 事件，实际事件: " + events);
    }

    @Test
    void ofTransmitsDelegateInterface() {
        // 输入什么接口就输出什么接口：of() 返回与被包装 delegate 相同的接口层级
        MemoryCache<String, String> memory = Caches.<String, String>memory().get();
        Duration d = Duration.ofMinutes(1);

        Cache<String, String> asCache = RefreshingCacheAdapter.of(memory, d, k -> "v");
        BoundedCache<String, String> asBounded = RefreshingCacheAdapter.of(memory, d, k -> "v");
        MemoryCache<String, String> asMemory = RefreshingCacheAdapter.of(memory, d, k -> "v");

        assertTrue(asCache instanceof RefreshingCacheAdapter, "of(Cache) 应返回刷新适配器");
        assertTrue(asBounded instanceof RefreshingCacheAdapter, "of(BoundedCache) 应返回刷新适配器");
        assertTrue(asMemory instanceof RefreshingCacheAdapter, "of(MemoryCache) 应返回刷新适配器");
    }

    @Test
    void ofObservableTransmitsListenerAndRefreshEvents() throws Exception {
        // of(ObservableMemoryCache) 返回可观测视图：addListener 透传给内层，刷新写回触发 PUT 事件
        List<String> events = new CopyOnWriteArrayList<>();
        MemoryCache<String, String> store = Caches.<String, String>memory().get();
        ObservableMemoryCache<String, String> obs = CacheListenerAdapter.of(store);

        ObservableMemoryCache<String, String> refreshing =
                RefreshingCacheAdapter.of(obs, Duration.ofMillis(100), k -> "fresh");
        refreshing.addListener(CacheEventType.PUT, (type, key, oldV, newV) -> events.add("PUT:" + key));

        refreshing.put("k", "old");
        Thread.sleep(150); // 超过刷新间隔
        assertEquals("old", refreshing.get("k"), "刷新前返回旧值");

        long deadline = System.currentTimeMillis() + 5000;
        while (events.stream().noneMatch(e -> e.equals("PUT:k")) && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
        assertTrue(events.stream().anyMatch(e -> e.equals("PUT:k")),
                "of(ObservableMemoryCache) 应透传监听且刷新写回触发事件，实际: " + events);
    }
}
