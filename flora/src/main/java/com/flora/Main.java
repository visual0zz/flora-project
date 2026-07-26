package com.flora;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.stats.CacheStats;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class Main {

    public static void main(String[] args) {
        basicCache();
        loadingCache();
        sizeBasedEviction();
        timeBasedExpiration();
        statistics();
        removalListener();
    }

    /** 基础 put / get / getIfPresent */
    private static void basicCache() {
        section("基础缓存");
        Cache<String, Integer> cache = Caffeine.newBuilder().build();

        cache.put("a", 1);
        cache.put("b", 2);
        System.out.println("get a = " + cache.getIfPresent("a"));      // 1
        System.out.println("getIfPresent c = " + cache.getIfPresent("c")); // null
        System.out.println("get c (compute) = " + cache.get("c", k -> k.length())); // 1

        System.out.println("asMap = " + cache.asMap());
        cache.invalidate("a");
        System.out.println("after invalidate a, size = " + cache.estimatedSize());
    }

    /** 自动加载：键不存在时通过 loader 计算 */
    private static void loadingCache() {
        section("LoadingCache 自动加载");
        LoadingCache<String, String> cache = Caffeine.newBuilder()
                .build(key -> "value-" + key.toUpperCase());

        System.out.println("get foo = " + cache.get("foo"));   // value-FOO
        System.out.println("getAll [x,y] = " + cache.getAll(java.util.List.of("x", "y")));
    }

    /** 基于容量（权重）的淘汰：LRU/W-TinyLFU */
    private static void sizeBasedEviction() {
        section("基于大小的淘汰 (maximumWeight)");
        Cache<Integer, Integer> cache = Caffeine.newBuilder()
                .maximumWeight(10)
                .weigher((Integer k, Integer v) -> v)
                .build();

        IntStream.range(0, 20).forEach(i -> cache.put(i, 1));   // 每个权重 1，最多容纳 10 个
        cache.cleanUp();                                          // 强制维护，触发淘汰（淘汰是惰性/异步的）
        System.out.println("estimatedSize = " + cache.estimatedSize());
        System.out.println("contains 0? " + (cache.getIfPresent(0) != null)); // 大概率已被淘汰
    }

    /** 基于时间的过期 */
    private static void timeBasedExpiration() {
        section("基于时间的过期");
        Cache<String, String> cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMillis(50))
                .build();

        cache.put("k", "v");
        System.out.println("immediately get k = " + cache.getIfPresent("k")); // v
        sleep(80);
        System.out.println("after 80ms get k = " + cache.getIfPresent("k"));  // null
    }

    /** 统计信息：命中率、加载次数等 */
    private static void statistics() {
        section("统计信息");
        Cache<String, Integer> cache = Caffeine.newBuilder()
                .recordStats()
                .build();

        cache.get("hit1", k -> 100);  // miss + load
        cache.get("hit1", k -> 100);  // hit
        cache.get("hit2", k -> 200);  // miss + load
        CacheStats stats = cache.stats();
        System.out.println("hitRate    = " + stats.hitRate());
        System.out.println("hitCount   = " + stats.hitCount());
        System.out.println("missCount  = " + stats.missCount());
        System.out.println("evictionCount = " + stats.evictionCount());
    }

    /** 移除监听器 */
    private static void removalListener() {
        section("移除监听器");
        AtomicInteger removed = new AtomicInteger();
        Cache<String, String> cache = Caffeine.newBuilder()
                .removalListener((String key, String value, RemovalCause cause) -> {
                    removed.incrementAndGet();
                    System.out.println("removed key=" + key + " cause=" + cause);
                })
                .expireAfterWrite(Duration.ofMillis(30))
                .build();

        cache.put("x", "1");
        cache.put("y", "2");
        cache.invalidate("x");  // 手动移除 -> 触发监听
        sleep(50);              // 触发 y 的过期移除
        cache.cleanUp();
        sleep(200);             // 等待异步移除监听器完成（监听器在公共线程池执行）
        System.out.println("total removed = " + removed.get());
    }

    private static void section(String title) {
        System.out.println("\n===== " + title + " =====");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
