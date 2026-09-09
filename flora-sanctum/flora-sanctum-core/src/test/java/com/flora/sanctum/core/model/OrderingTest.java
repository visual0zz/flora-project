package com.flora.sanctum.core.model;

import com.flora.root.container.order.RocicorpFractionalIndex;
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
        String oa = t.context().orderOf(a.uuid());
        String ob = t.context().orderOf(b.uuid());
        String oc = t.context().orderOf(c.uuid());
        assertTrue(oa.compareTo(ob) < 0 && ob.compareTo(oc) < 0, "创建顺序应反映为 order 递增");
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
        String oa = t.context().orderOf(a.uuid());
        String oc = t.context().orderOf(c.uuid());
        // 把 B 移到 A 之前
        s.moveTo(b.uuid(), s.rootObjectUuid(), a.uuid());
        assertEquals(oa, t.context().orderOf(a.uuid()), "A 的 order 不应变");
        assertEquals(oc, t.context().orderOf(c.uuid()), "C 的 order 不应变");
        assertTrue(t.context().orderOf(b.uuid()).compareTo(oa) < 0, "B 应插到 A 之前");
        List<UUID> order = t.rootEntries().stream().map(EntryNode::uuid).toList();
        assertEquals(List.of(b.uuid(), a.uuid(), c.uuid()), order);
        s.close();
    }

    /** 同父内把中间元素后移：期望落在目标前驱之后，而不是被挤到列表前部。 */
    @Test
    void reorderWithinSameParentKeepsExactPosition(@TempDir Path dir) {
        Sanctum s = newVault(dir);
        ObjectTree t = s.objectTree();
        EntryNode a = t.createEntry(null, "A", empty());
        EntryNode b = t.createEntry(null, "B", empty());
        EntryNode c = t.createEntry(null, "C", empty());
        EntryNode d = t.createEntry(null, "D", empty());
        // B 移到 D 之前：期望 A C B D
        s.moveTo(b.uuid(), s.rootObjectUuid(), d.uuid());
        assertEquals(List.of(a.uuid(), c.uuid(), b.uuid(), d.uuid()),
                t.rootEntries().stream().map(EntryNode::uuid).toList());
        s.close();
    }

    @Test
    void iconEditPreservesOrderAndPosition(@TempDir Path dir) {
        Sanctum s = newVault(dir);
        ObjectTree t = s.objectTree();
        EntryNode a = t.createEntry(null, "A", empty());
        EntryNode b = t.createEntry(null, "B", empty());
        EntryNode c = t.createEntry(null, "C", empty());
        String oa = t.context().orderOf(a.uuid());
        String ob = t.context().orderOf(b.uuid());
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

    /**
     * 反复插到最前：小数索引精度无界，不触发重排，每次的精确位置都应保持。
     * 断言精确列表而非仅单调性——单调性会放过「元素被放到错误邻居旁」的缺陷。
     */
    @Test
    void repeatedHeadInsertKeepsExactOrder(@TempDir Path dir) {
        Sanctum s = newVault(dir);
        ObjectTree t = s.objectTree();
        UUID root = s.rootObjectUuid();
        List<UUID> expected = new ArrayList<>();
        EntryNode a = t.createEntry(null, "A", empty());
        EntryNode b = t.createEntry(null, "B", empty());
        expected.add(a.uuid());
        expected.add(b.uuid());
        for (int i = 0; i < 120; i++) {
            EntryNode n = t.createEntry(null, "x" + i, empty());
            s.moveTo(n.uuid(), root, expected.get(0));
            expected.add(0, n.uuid());
        }
        assertEquals(expected, t.rootEntries().stream().map(EntryNode::uuid).toList());
        s.close();
        // 重开仍有序
        Sanctum s2 = Sanctum.open(dir.resolve("vault"));
        s2.unlock("pw".toCharArray());
        assertEquals(expected, s2.objectTree().rootEntries().stream().map(EntryNode::uuid).toList());
        s2.close();
    }

    /**
     * order 不是字符串（缺失或旧格式数值）时不应用作排序键，扫描时应按当前顺序重赋。
     * 旧数据本就没有字符串 order，其展示顺序也是扫描顺序，故按扫描顺序赋序恰好延续原次序。
     */
    @Test
    void nonStringOrderIsReassigned(@TempDir Path dir) {
        Sanctum s = newVault(dir);
        ObjectTree t = s.objectTree();
        EntryNode a = t.createEntry(null, "A", empty());
        EntryNode b = t.createEntry(null, "B", empty());
        for (EntryNode n : List.of(a, b)) {
            JsonObject o = t.context().read(n.uuid());
            o.remove("order");
            o.put("order", 4_600_000_000_000_000_000L);
            t.context().write(n.uuid(), o, s.rootObjectUuid());
        }
        s.close();

        Sanctum s2 = Sanctum.open(dir.resolve("vault"));
        s2.unlock("pw".toCharArray());
        List<UUID> migrated = s2.objectTree().rootEntries().stream().map(EntryNode::uuid).toList();
        assertEquals(2, migrated.size());
        assertMonotonic(s2.objectTree(), migrated);
        for (UUID u : migrated) {
            assertNotNull(s2.objectTree().context().orderOf(u), "应被重新赋为合法 order");
        }
        s2.close();
        // 再次重开：赋序为惰性落盘，但扫描顺序确定，故展示次序应保持稳定
        Sanctum s3 = Sanctum.open(dir.resolve("vault"));
        s3.unlock("pw".toCharArray());
        assertEquals(migrated, s3.objectTree().rootEntries().stream().map(EntryNode::uuid).toList());
        s3.close();
    }

    private void assertMonotonic(ObjectTree t, List<UUID> order) {
        String prev = null;
        for (UUID u : order) {
            String o = t.context().orderOf(u);
            assertTrue(RocicorpFractionalIndex.compare(prev, o) < 0, "order 应单调递增");
            prev = o;
        }
    }
}
