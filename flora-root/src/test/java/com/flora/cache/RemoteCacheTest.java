package com.flora.cache;

import com.flora.cache.interfaces.Cache;
import com.flora.cache.interfaces.ObservableCache;
import com.flora.cache.interfaces.RemoteStore;
import com.flora.cache.impl.RemoteCache;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class RemoteCacheTest {

    /** 内存 RemoteStore 假实现（Redis 语义子集）。 */
    private static RemoteStore memStore() {
        Map<String, String> data = new ConcurrentHashMap<>();
        Map<String, Long> expiry = new ConcurrentHashMap<>();
        return new RemoteStore() {
            @Override public void set(String key, String value, long ttlMillis) {
                if (ttlMillis <= 0 && ttlMillis != -1) {
                    data.remove(key);
                    expiry.remove(key);
                    return;
                }
                data.put(key, value);
                if (ttlMillis == -1) expiry.remove(key);
                else expiry.put(key, System.currentTimeMillis() + ttlMillis);
            }
            @Override public String get(String key) {
                return data.get(key);
            }
            @Override public boolean setNx(String key, String value, long ttlMillis) {
                if (data.containsKey(key)) return false;
                set(key, value, ttlMillis);
                return true;
            }
            @Override public boolean expire(String key, long ttlMillis) {
                if (!data.containsKey(key)) return false;
                if (ttlMillis == -1) expiry.remove(key);
                else expiry.put(key, System.currentTimeMillis() + ttlMillis);
                return true;
            }
            @Override public long ttl(String key) {
                if (!data.containsKey(key)) return -2;
                Long exp = expiry.get(key);
                if (exp == null) return -1;
                long remain = exp - System.currentTimeMillis();
                return remain > 0 ? remain : -2;
            }
            @Override public long delete(String key) {
                expiry.remove(key);
                return data.remove(key) != null ? 1 : 0;
            }
            @Override public boolean exists(String key) {
                return data.containsKey(key);
            }
            @Override public long size() {
                return data.size();
            }
            @Override public void clear() {
                data.clear();
                expiry.clear();
            }
        };
    }

    @Test
    void ofProxiesRemoteStore() {
        RemoteCache cache = RemoteCache.of(memStore());

        cache.put("k", "v");
        assertEquals("v", cache.get("k"));
        assertTrue(cache.containsKey("k"));
        assertFalse(cache.putIfAbsent("k", "other"), "已存在时 putIfAbsent 应失败");
        assertEquals("v", cache.get("k"), "值不应被覆盖");
        assertTrue(cache.putIfAbsent("new", "n"));
        assertEquals(2, cache.approxCount());

        cache.put("exp", "x", Duration.ofMillis(10));
        assertTrue(cache.ttl("exp").compareTo(Duration.ZERO) > 0, "带 TTL 写入后应有剩余过期时间");
        assertEquals(Duration.MAX, cache.ttl("k"), "无 TTL 的键应返回 Duration.MAX");

        assertEquals("v", cache.remove("k"));
        assertNull(cache.remove("k"), "已删除的键 remove 返回 null");
        assertFalse(cache.containsKey("k"));

        cache.clear();
        assertEquals(0, cache.approxCount());
    }

    @Test
    void cachesRemoteChainBuilds() {
        Cache<String, String> cache = Caches.remote().store(memStore()).get();
        cache.put("a", "1");
        assertEquals("1", cache.get("a"));
    }

    @Test
    void cachesRemoteObservableTransmitsListener() {
        List<String> events = new CopyOnWriteArrayList<>();
        Cache<String, String> cache = Caches.<String, String>remote()
                .store(memStore())
                .listener(CacheEventType.PUT, (type, key, oldV, newV) -> events.add("PUT:" + key))
                .get();

        cache.put("k", "v");
        assertTrue(events.stream().anyMatch(e -> e.equals("PUT:k")),
                "remote().listener() 应派发 PUT 事件，实际: " + events);
        assertTrue(cache instanceof ObservableCache, "observable 时返回可观测视图");
    }
}
