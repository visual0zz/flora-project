package com.flora.root.runtime.config.impl;

import com.flora.root.runtime.config.ConfigUtil;
import com.flora.root.runtime.config.interfaces.ReloadableConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ReloadableConfigImpl} 的单元测试（含并发语义）。
 */
class ReloadableConfigImplTest {

    @Test
    void replaceWithReplacesWhole() {
        ReloadableConfig r = new ReloadableConfigImpl();
        r.replaceWith(ConfigUtil.newConfig().loadFromString("a=1\nb=2").build());
        assertEquals("1", r.get("a"));

        r.replaceWith(ConfigUtil.newConfig().loadFromString("b=3").build());
        assertNull(r.get("a"));   // 未在新配置中 -> 被替换掉
        assertEquals("3", r.get("b"));
    }

    @Test
    void refreshWithMergesKeepingOld() {
        ReloadableConfig r = new ReloadableConfigImpl();
        r.replaceWith(ConfigUtil.newConfig().loadFromString("a=1\nb=2").build());
        r.refreshWith(ConfigUtil.newConfig().loadFromString("b=3\nc=4").build());
        assertEquals("1", r.get("a"));   // 无新值 -> 保留
        assertEquals("3", r.get("b"));   // 新值覆盖
        assertEquals("4", r.get("c"));
    }

    @Test
    void refreshWithNullIsNoop() {
        ReloadableConfig r = new ReloadableConfigImpl();
        r.replaceWith(ConfigUtil.newConfig().loadFromString("a=1").build());
        r.refreshWith(null);
        assertEquals("1", r.get("a"));
    }

    @Test
    void concurrentRefreshNoLostUpdate() throws Exception {
        int threads = 50;
        ReloadableConfig r = new ReloadableConfigImpl();
        r.replaceWith(ConfigUtil.newConfig().loadFromString("base=0").build());

        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            int idx = i;
            Thread t = new Thread(() -> {
                ready.countDown();
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                r.refreshWith(ConfigUtil.newConfig().loadFromString("k" + idx + "=" + idx).build());
            });
            workers.add(t);
            t.start();
        }
        ready.await();
        start.countDown();
        for (Thread t : workers) {
            t.join();
        }

        assertEquals("0", r.get("base"));
        for (int i = 0; i < threads; i++) {
            assertEquals(String.valueOf(i), r.get("k" + i), "key k" + i + " 更新被丢失");
        }
    }

    @Test
    void concurrentReadsAreSafe() throws Exception {
        ReloadableConfig r = new ReloadableConfigImpl();
        r.replaceWith(ConfigUtil.newConfig().loadFromString("a=1\nb=2").build());

        CountDownLatch start = new CountDownLatch(1);
        List<Thread> readers = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Thread t = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int j = 0; j < 1000; j++) {
                    assertEquals("1", r.get("a"));
                    assertFalse(r.isEmpty());
                    assertNotNull(r.toMapTree());
                }
            });
            readers.add(t);
            t.start();
        }
        start.countDown();
        for (Thread t : readers) {
            t.join();
        }
    }
}
