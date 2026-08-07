package com.flora.cache;

import com.flora.cache.eviction.LRUEvictionPolicy;
import com.flora.cache.store.CacheListenerAdapter;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class InternalRemovalDispatchTest {

    @Test
    void evictDispatchesEventWithOldValue() {
        // 用 LRU + 容量 2：插入第 3 个键时淘汰最早写入的 "a"
        AtomicReference<MemoryCache<String, String>> ref = new AtomicReference<>();
        LRUEvictionPolicy<String, String> policy =
                new LRUEvictionPolicy<>(2, () -> ref.get().approxCount());
        MemoryCache<String, String> store = Caches.<String, String>memory().capacity(2).evict(policy).get();
        ref.set(store);

        ObservableMemoryCache<String, String> obs = CacheListenerAdapter.of(store);

        List<String> evicted = new ArrayList<>();
        obs.addListener(CacheEventType.EVICT, (t, k, oldV, newV) -> evicted.add(oldV));

        obs.put("a", "va");
        obs.put("b", "vb");
        obs.put("c", "vc"); // 触发容量淘汰

        assertEquals(List.of("va"), evicted, "逐出时应派发 EVICT 且 oldValue 为被移除的值");
    }

    @Test
    void expireDispatchesEventWithOldValue() throws InterruptedException {
        MemoryCache<String, String> store = Caches.<String, String>memory().get();
        ObservableMemoryCache<String, String> obs = CacheListenerAdapter.of(store);

        List<String> expired = new ArrayList<>();
        obs.addListener(CacheEventType.EXPIRE, (t, k, oldV, newV) -> expired.add(oldV));

        obs.put("a", "va", Duration.ofMillis(1));
        Thread.sleep(10);
        obs.cleanUp(); // 主动扫描触发过期删除

        assertEquals(List.of("va"), expired, "过期时应派发 EXPIRE 且 oldValue 为被移除的值");
    }
}
