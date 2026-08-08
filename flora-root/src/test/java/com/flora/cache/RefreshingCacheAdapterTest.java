package com.flora.cache;

import com.flora.cache.interfaces.MemoryCache;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class RefreshingCacheAdapterTest {

    /** 记录任务的 executor：不执行，由测试手动触发，用于观察刷新提交时序。 */
    private static Executor recorder(List<Runnable> tasks) {
        return tasks::add;
    }

    private static MemoryCache<String, String> refreshingCache(
            Function<String, String> loader, Duration refreshAfter, List<Runnable> tasks) {
        return Caches.<String, String>memory()
                .refreshing(loader, refreshAfter, recorder(tasks))
                .get();
    }

    @Test
    void getReturnsStaleValueAndRefreshesInBackground() throws Exception {
        List<Runnable> tasks = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        MemoryCache<String, String> cache = refreshingCache(
                k -> "v" + calls.incrementAndGet(), Duration.ofMillis(1), tasks);

        cache.put("k", "initial");
        // 未超间隔：返回旧值，不触发刷新
        assertEquals("initial", cache.get("k"));
        assertTrue(tasks.isEmpty(), "未到刷新间隔不应提交刷新");

        Thread.sleep(5); // 超过 1ms 刷新间隔
        // 超过间隔：仍返回旧值，但提交了刷新任务
        assertEquals("initial", cache.get("k"));
        assertEquals(1, tasks.size(), "超过间隔应提交一次刷新");
        assertEquals("initial", cache.get("k"), "刷新完成前重复读取仍返回旧值");
        assertEquals(1, tasks.size(), "刷新任务未执行期间重复读取应合并，不再提交");

        // 执行后台刷新
        tasks.forEach(Runnable::run);
        tasks.clear();
        assertEquals("v1", cache.get("k"), "刷新后值已更新");
    }

    @Test
    void missingKeyLoadsSynchronously() {
        List<Runnable> tasks = new ArrayList<>();
        MemoryCache<String, String> cache = refreshingCache(
                k -> "loaded", Duration.ofMinutes(1), tasks);

        assertEquals("loaded", cache.get("missing"));
        assertTrue(cache.containsKey("missing"), "同步加载应写入缓存");
        assertTrue(tasks.isEmpty(), "缺失加载不应走异步刷新");
    }

    @Test
    void refreshFailureKeepsStaleValueAndRetries() throws Exception {
        List<Runnable> tasks = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        MemoryCache<String, String> cache = refreshingCache(k -> {
            if (calls.incrementAndGet() == 1) throw new IllegalStateException("boom");
            return "fresh";
        }, Duration.ofMillis(1), tasks);

        cache.put("k", "old");
        Thread.sleep(5);
        assertEquals("old", cache.get("k"), "刷新前返回旧值");
        tasks.forEach(Runnable::run); // 刷新抛异常，旧值保留
        tasks.clear();

        // 失败不推进计时 → 下次读取继续尝试刷新
        Thread.sleep(5);
        assertEquals("old", cache.get("k"));
        assertEquals(1, tasks.size(), "刷新失败后应再次提交");
        tasks.forEach(Runnable::run);
        assertEquals("fresh", cache.get("k"));
    }

    @Test
    void putResetsRefreshTimer() throws Exception {
        List<Runnable> tasks = new ArrayList<>();
        MemoryCache<String, String> cache = refreshingCache(
                k -> "new", Duration.ofMillis(1), tasks);

        cache.put("k", "old");
        Thread.sleep(5);
        cache.put("k", "updated"); // 手动写回应重置计时
        assertEquals("updated", cache.get("k"));
        assertTrue(tasks.isEmpty(), "put 后未到间隔不应触发刷新");
    }

    @Test
    void concurrentGetsSubmitSingleRefresh() throws Exception {
        CopyOnWriteArrayList<Runnable> tasks = new CopyOnWriteArrayList<>();
        MemoryCache<String, String> cache = refreshingCache(
                k -> "fresh", Duration.ofMillis(1), tasks);

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
        assertEquals(1, tasks.size(), "并发读取同一 key 应只提交一次刷新");
    }

    @Test
    void refreshWriteTriggersPutEventWhenObservable() throws Exception {
        // 验证组合顺序：refreshing 外层、observable 内层，后台刷新写回应派发 PUT 事件
        List<Runnable> tasks = new ArrayList<>();
        List<String> events = new CopyOnWriteArrayList<>();
        MemoryCache<String, String> cache = Caches.<String, String>memory()
                .refreshing(k -> "fresh", Duration.ofMillis(1), recorder(tasks))
                .listener(CacheEventType.PUT, (type, key, oldV, newV) -> events.add("PUT:" + key + "=" + newV))
                .get();

        cache.put("k", "old");
        Thread.sleep(5);
        assertEquals("old", cache.get("k"), "刷新前返回旧值");
        tasks.forEach(Runnable::run); // 执行后台刷新写回
        assertTrue(events.stream().anyMatch(e -> e.equals("PUT:k=fresh")),
                "后台刷新写回应派发 PUT 事件，实际事件: " + events);
    }
}
