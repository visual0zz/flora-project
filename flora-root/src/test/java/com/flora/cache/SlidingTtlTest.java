package com.flora.cache;

import com.flora.cache.interfaces.MemoryCache;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 滑动 TTL（空闲超时）语义验证：{@code get(key, ttl)} 命中时顺延过期时刻。
 */
class SlidingTtlTest {

    @Test
    void getWithTtlKeepsEntryAlivePastFixedDeadline() throws InterruptedException {
        MemoryCache<String, String> cache = Caches.<String, String>memory().get();
        Duration ttl = Duration.ofSeconds(2);

        cache.put("k", "v", ttl);
        Thread.sleep(1100);                     // 距写入 1.1s，仍存活
        assertEquals("v", cache.get("k", ttl)); // 命中并顺延至 now + 2s
        Thread.sleep(1100);                     // 距写入 2.2s（已超过原 2s 死线），滑动续期后仍存活
        assertEquals("v", cache.get("k"), "滑动续期后不应在原固定死线处过期");
        Thread.sleep(1400);                     // 距上次顺延 1.4s > 2s 窗口
        assertNull(cache.get("k"), "闲置满窗口后应过期回收");
    }

    @Test
    void plainGetDoesNotRefreshDeadline() throws InterruptedException {
        MemoryCache<String, String> cache = Caches.<String, String>memory().get();
        cache.put("k", "v", Duration.ofSeconds(1));
        Thread.sleep(300);
        assertEquals("v", cache.get("k"));      // 普通读取不续期
        Thread.sleep(900);                      // 300+900=1200 > 1000，已超过 put 时的死线
        assertNull(cache.get("k"), "普通 get 不续期，应在 put 时的固定死线处过期");
    }

    @Test
    void getWithNonPositiveOrMaxTtlDoesNotRefresh() throws InterruptedException {
        MemoryCache<String, String> cache = Caches.<String, String>memory().get();
        cache.put("k", "v", Duration.ofSeconds(1));
        Thread.sleep(400);
        assertEquals("v", cache.get("k", Duration.ZERO));  // 非正 ttl = 纯读取，不续期
        assertEquals("v", cache.get("k", Duration.MAX));   // MAX = 纯读取，不续期也不改成永久
        Thread.sleep(800);                                 // 400+800=1200 > 1000，仍按 put 时的死线过期
        assertNull(cache.get("k"));
    }
}
