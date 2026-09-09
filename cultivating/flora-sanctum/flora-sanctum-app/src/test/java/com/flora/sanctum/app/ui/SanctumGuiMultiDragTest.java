package com.flora.sanctum.app.ui;

import com.flora.sanctum.core.model.Sanctum;
import com.flora.sanctum.core.model.tree.EntryNode;
import com.flora.sanctum.core.model.tree.GroupNode;
import com.flora.sanctum.core.model.tree.ObjectTree;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.DefaultListModel;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 回归测试：多选拖拽的两个核心规则。
 * <p>headless 下真实构造 GUI 并反射调用私有 {@code performMove(List, targetGroup, beforeUuid)}：
 * <ul>
 *   <li>冲突：选择同时包含某组与其内部子组时整体拒绝（statusLabel 含「无法移动」）。</li>
 *   <li>多选一起移动：选中的多个条目同时落到目标之下，其余不动。</li>
 * </ul>
 */
class SanctumGuiMultiDragTest {

    @Test
    void selectingGroupAndItsChildRejects(@TempDir Path dir) throws Exception {
        System.setProperty("java.awt.headless", "true");
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignore) {
        }

        Path vault = dir.resolve("vault");
        String pw = "test";
        Sanctum sanctum = Sanctum.createAndUnlock(vault, pw.toCharArray(), 8192, 2, 1);
        ObjectTree tree = sanctum.objectTree();
        GroupNode g = tree.createGroup(null, "G");
        GroupNode s = tree.createGroup(g.uuid(), "S"); // S 是 G 的子组
        sanctum.close();

        Sanctum s2 = Sanctum.open(vault);
        s2.unlock(pw.toCharArray());

        SanctumGui gui = newGui(s2);
        Method performMove = SanctumGui.class.getDeclaredMethod(
                "performMove", List.class, UUID.class, UUID.class);
        performMove.setAccessible(true);
        // 同时选中 G 与其子组 S，拖到任意目标 → 应被冲突规则拒绝
        performMove.invoke(gui, List.of(g.uuid(), s.uuid()), UUID.randomUUID(), null);

        s2.close();
        JLabel status = (JLabel) readField(gui, "statusLabel");
        assertTrue(status.getText().contains("无法移动"),
                "同时选中组与其内部节点应拒绝移动，实际状态：" + status.getText());
    }

    @Test
    void multipleEntriesMoveTogether(@TempDir Path dir) throws Exception {
        System.setProperty("java.awt.headless", "true");
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignore) {
        }

        Path vault = dir.resolve("vault");
        String pw = "test";
        Sanctum sanctum = Sanctum.createAndUnlock(vault, pw.toCharArray(), 8192, 2, 1);
        ObjectTree tree = sanctum.objectTree();
        GroupNode h = tree.createGroup(null, "H");
        EntryNode a = tree.createEntry(h.uuid(), "A", new com.flora.sanctum.core.model.EntryFields(null, null, null, java.util.List.of()));
        EntryNode b = tree.createEntry(h.uuid(), "B", new com.flora.sanctum.core.model.EntryFields(null, null, null, java.util.List.of()));
        EntryNode c = tree.createEntry(h.uuid(), "C", new com.flora.sanctum.core.model.EntryFields(null, null, null, java.util.List.of()));
        UUID root = sanctum.rootObjectUuid();
        sanctum.close();

        Sanctum s2 = Sanctum.open(vault);
        s2.unlock(pw.toCharArray());

        SanctumGui gui = newGui(s2);
        Method performMove = SanctumGui.class.getDeclaredMethod(
                "performMove", List.class, UUID.class, UUID.class);
        performMove.setAccessible(true);
        // 同时选中子组 H 内的 A、B 拖到根（顶层），C 不动
        performMove.invoke(gui, List.of(a.uuid(), b.uuid()), root, null);

        s2.close();
        Sanctum s3 = Sanctum.open(vault);
        s3.unlock(pw.toCharArray());
        List<UUID> rootOrder = s3.objectTree().rootEntries().stream().map(EntryNode::uuid).toList();
        List<UUID> hOrder = s3.objectTree().group(h.uuid()).entries().stream().map(EntryNode::uuid).toList();
        s3.close();

        assertEquals(List.of(a.uuid(), b.uuid()), rootOrder,
                "多选 A、B 一起移动到根后，根应有 [A, B]");
        assertEquals(List.of(c.uuid()), hOrder,
                "未被选中的 C 应留在原组 H 内，不被移动");
    }

    private static SanctumGui newGui(Sanctum sanctum) throws Exception {
        Constructor<SanctumGui> ctor = SanctumGui.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        SanctumGui gui = ctor.newInstance();
        setField(gui, "sanctum", sanctum);
        setField(gui, "frame", null);
        setField(gui, "groupTree", new JTree());
        setField(gui, "entryList", new JList<>());
        setField(gui, "entryModel", new DefaultListModel<>());
        setField(gui, "statusLabel", new JLabel());
        return gui;
    }

    private static Object readField(Object target, String name) throws Exception {
        Field f = SanctumGui.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = SanctumGui.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
