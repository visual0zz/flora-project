package com.flora.sanctum.core.model;

import com.flora.sanctum.core.model.tree.EntryNode;
import com.flora.sanctum.core.model.tree.FieldNode;
import com.flora.sanctum.core.model.tree.GroupNode;
import com.flora.sanctum.core.model.tree.ObjectTree;
import com.flora.sanctum.core.model.impl.TreeContext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 并发冒烟：多线程交替对同一会话做建条目/建字段/删除，验证 TreeContext 的锁能保证
 * 索引与对象图一致、无时间戳碰撞、无 IllegalStateException / ConcurrentModificationException。
 */
class TreeContextConcurrencyTest {

    @TempDir
    Path dir;

    @Test
    void concurrentWritesKeepIndexConsistent() throws Exception {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray(), 8192, 2, 1);
        ObjectTree tree = s.objectTree();
        TreeContext ctx = tree.context();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        int threads = 8;
        int perThread = 64;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        List<UUID> createdEntries = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            final int seed = t;
            Thread th = new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        EntryNode entry = tree.createEntry(null,
                                "entry-" + seed + "-" + i, EntryFields.EMPTY);
                        synchronized (createdEntries) {
                            createdEntries.add(entry.uuid());
                        }
                        // 追加自定义字段（走 ctx.write）
                        FieldNode f = entry.writeField("note-" + i, "v", null);
                        // 立即读取 childrenOf，验证索引在并发下可用且一致
                        List<UUID> kids = ctx.childrenOf(entry.uuid());
                        assertTrue(kids.contains(f.uuid()), "字段应出现在条目的 childrenOf");
                        f.delete();
                        entry.delete();
                    }
                } catch (Throwable ex) {
                    failure.compareAndSet(null, ex);
                } finally {
                    done.countDown();
                }
            }, "writer-" + t);
            th.start();
        }

        start.countDown();
        done.await();

        assertNull(failure.get(), "并发写入不应抛异常");

        // 全部删除后，索引与对象图应一致：条目/字段节点不再出现，childrenOf 为空
        for (UUID e : createdEntries) {
            assertNull(ctx.read(e), "已删除条目应从对象图移除");
            assertTrue(ctx.childrenOf(e).isEmpty(), "已删除条目的 childrenOf 应为空");
        }
        // 索引不应残留孤儿：遍历对象图，每个对象的 parent 在 childrenByParent 中必须映射回它
        Map<UUID, java.util.List<UUID>> expected = new ConcurrentHashMap<>();
        for (Map.Entry<UUID, com.flora.root.codec.json.model.JsonObject> e : ctx.objects().entrySet()) {
            UUID parent = ctx.parentUuidOf(e.getKey());
            if (parent != null) {
                expected.computeIfAbsent(parent, k -> new ArrayList<>()).add(e.getKey());
            }
        }
        for (Map.Entry<UUID, java.util.List<UUID>> e : expected.entrySet()) {
            assertEquals(new ArrayList<>(e.getValue()),
                    new ArrayList<>(ctx.childrenOf(e.getKey())),
                    "childrenOf 应与 parent 映射一致");
        }
    }
}
