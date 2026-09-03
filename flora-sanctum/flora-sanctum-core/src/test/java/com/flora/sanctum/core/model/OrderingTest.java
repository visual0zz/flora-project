package com.flora.sanctum.core.model;

import com.flora.root.codec.json.model.JsonObject;
import com.flora.sanctum.core.model.tree.EntryNode;
import com.flora.sanctum.core.model.tree.ObjectTree;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderingTest {

    private static EntryFields empty() {
        return new EntryFields(null, null, null, java.util.List.of());
    }

    private Sanctum newVault(Path dir) {
        return Sanctum.createAndUnlock(dir.resolve("vault"), "pw".toCharArray(), 8192, 2, 1);
    }

    @Test
    void createAppendsToEndByOrder(@TempDir Path dir) {
        Sanctum s = newVault(dir);
        ObjectTree t = s.objectTree();
        EntryNode a = t.createEntry(null, "A", empty());
        EntryNode b = t.createEntry(null, "B", empty());
        EntryNode c = t.createEntry(null, "C", empty());
        long oa = t.context().orderOf(a.uuid());
        long ob = t.context().orderOf(b.uuid());
        long oc = t.context().orderOf(c.uuid());
        assertTrue(oa < ob && ob < oc, "创建顺序应反映为 order 递增");
        List<UUID> order = t.rootEntries().stream().map(EntryNode::uuid).toList();
        assertEquals(List.of(a.uuid(), b.uuid(), c.uuid()), order, "rootEntries 应按 order 升序");
        s.close();
    }

    @Test
    void reorderChangesOnlyMoved(@TempDir Path dir) {
        Sanctum s = newVault(dir);
        ObjectTree t = s.objectTree();
        EntryNode a = t.createEntry(null, "A", empty());
        EntryNode b = t.createEntry(null, "B", empty());
        EntryNode c = t.createEntry(null, "C", empty());
        long oa = t.context().orderOf(a.uuid());
        long oc = t.context().orderOf(c.uuid());
        // 把 B 移到 A 之前
        s.moveTo(b.uuid(), s.rootObjectUuid(), a.uuid());
        assertEquals(oa, t.context().orderOf(a.uuid()), "A 的 order 不应变");
        assertEquals(oc, t.context().orderOf(c.uuid()), "C 的 order 不应变");
        assertTrue(t.context().orderOf(b.uuid()) < oa, "B 应插到 A 之前");
        List<UUID> order = t.rootEntries().stream().map(EntryNode::uuid).toList();
        assertEquals(List.of(b.uuid(), a.uuid(), c.uuid()), order);
        s.close();
    }

    @Test
    void iconEditPreservesOrderAndPosition(@TempDir Path dir) {
        Sanctum s = newVault(dir);
        ObjectTree t = s.objectTree();
        EntryNode a = t.createEntry(null, "A", empty());
        EntryNode b = t.createEntry(null, "B", empty());
        EntryNode c = t.createEntry(null, "C", empty());
        long oa = t.context().orderOf(a.uuid());
        long ob = t.context().orderOf(b.uuid());
        // 改 A 的图标（这正是上一轮导致顺序跳动的 bug）
        a.setIcon(UUID.randomUUID());
        assertEquals(oa, t.context().orderOf(a.uuid()), "改图标不应改变 order");
        assertEquals(ob, t.context().orderOf(b.uuid()));
        assertEquals(List.of(a.uuid(), b.uuid(), c.uuid()),
                t.rootEntries().stream().map(EntryNode::uuid).toList(), "改图标后顺序应不变");
        // 重开仓库，排序由持久化 order 决定，仍应稳定
        s.close();
        Sanctum s2 = Sanctum.open(dir.resolve("vault"));
        s2.unlock("pw".toCharArray());
        assertEquals(List.of(a.uuid(), b.uuid(), c.uuid()),
                s2.objectTree().rootEntries().stream().map(EntryNode::uuid).toList());
        s2.close();
    }

    @Test
    void rebalanceTriggersAndRecovers(@TempDir Path dir) {
        Sanctum s = newVault(dir);
        ObjectTree t = s.objectTree();
        UUID root = s.rootObjectUuid();
        List<EntryNode> nodes = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            nodes.add(t.createEntry(null, "e" + i, empty()));
        }
        // 反复插到最前，压缩首部间隙直到触发 rebalance（X=32）
        for (int i = 0; i < 40; i++) {
            EntryNode n = t.createEntry(null, "x" + i, empty());
            s.moveTo(n.uuid(), root, nodes.get(0).uuid());
            nodes.add(0, n);
        }
        List<UUID> order = t.rootEntries().stream().map(EntryNode::uuid).toList();
        assertMonotonic(t, order);
        s.close();
        // 重开仍有序
        Sanctum s2 = Sanctum.open(dir.resolve("vault"));
        s2.unlock("pw".toCharArray());
        assertMonotonic(s2.objectTree(), s2.objectTree().rootEntries().stream().map(EntryNode::uuid).toList());
        s2.close();
    }

    /**
     * 旧版用 orderBits 存 double 的 IEEE-754 位模式（量级约 4.6e18，语义与 long order 不同）。
     * 重开时应丢弃该旧字段、按当前顺序重新赋序，保证旧库展示顺序不变且可被小数索引接管。
     */
    @Test
    void legacyDoubleBitsFieldIsMigrated(@TempDir Path dir) {
        Sanctum s = newVault(dir);
        ObjectTree t = s.objectTree();
        EntryNode a = t.createEntry(null, "A", empty());
        EntryNode b = t.createEntry(null, "B", empty());
        for (EntryNode n : List.of(a, b)) {
            JsonObject o = t.context().read(n.uuid());
            o.remove("order");
            o.put("orderBits", Double.doubleToLongBits(4.6e18));
            t.context().write(n.uuid(), o, s.rootObjectUuid());
        }
        s.close();

        // 重开：旧字段应被丢弃、按扫描顺序重新赋序。旧数据本就没有 order，其旧展示顺序也是扫描顺序，
        // 因此按扫描顺序赋序恰好延续了旧库原有的展示次序。
        Sanctum s2 = Sanctum.open(dir.resolve("vault"));
        s2.unlock("pw".toCharArray());
        List<UUID> migrated = s2.objectTree().rootEntries().stream().map(EntryNode::uuid).toList();
        assertEquals(2, migrated.size());
        assertMonotonic(s2.objectTree(), migrated);
        for (UUID u : migrated) {
            assertTrue(s2.objectTree().context().orderOf(u) > 0, "应被重新赋为正的 order");
            assertNull(s2.objectTree().context().read(u).getLong("orderBits"),
                    "旧 orderBits 应从内存对象中清除，下次写块时不再带出");
        }
        s2.close();
        // 再次重开：赋序为惰性落盘，但扫描顺序确定，故展示次序应保持稳定
        Sanctum s3 = Sanctum.open(dir.resolve("vault"));
        s3.unlock("pw".toCharArray());
        assertEquals(migrated, s3.objectTree().rootEntries().stream().map(EntryNode::uuid).toList());
        s3.close();
    }

    private void assertMonotonic(ObjectTree t, List<UUID> order) {
        long prev = -1L;
        for (UUID u : order) {
            long o = t.context().orderOf(u);
            assertTrue(o > prev, "order 应单调递增");
            prev = o;
        }
    }
}
