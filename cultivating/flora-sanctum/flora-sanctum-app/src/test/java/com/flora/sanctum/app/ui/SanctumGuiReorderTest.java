package com.flora.sanctum.app.ui;

import com.flora.sanctum.core.model.Sanctum;
import com.flora.sanctum.core.model.tree.EntryNode;
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
 * 回归测试：中间列表拖拽「落到某条目上」应把被拖条目插到该条目之前（小数索引相对排序）。
 * <p>headless 下真实构造 GUI 并调用 3 参 {@code performMove(dragged, targetGroup, beforeUuid)}，
 * 等价于 ListDragHandler 把条目落到另一条目上时的行为。断言 B 拖到 A 之前后，重开仓库顺序为
 * [B, A, C]（仅 B 的 order 改变）。</p>
 */
class SanctumGuiReorderTest {

    @Test
    void dragEntryBeforeAnotherReorders(@TempDir Path dir) throws Exception {
        System.setProperty("java.awt.headless", "true");
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignore) {
        }

        Path vault = dir.resolve("vault");
        String pw = "test";
        Sanctum sanctum = Sanctum.createAndUnlock(vault, pw.toCharArray(), 8192, 2, 1);
        ObjectTree tree = sanctum.objectTree();
        EntryNode a = tree.createEntry(null, "A", new com.flora.sanctum.core.model.EntryFields(null, null, null, java.util.List.of()));
        EntryNode b = tree.createEntry(null, "B", new com.flora.sanctum.core.model.EntryFields(null, null, null, java.util.List.of()));
        EntryNode c = tree.createEntry(null, "C", new com.flora.sanctum.core.model.EntryFields(null, null, null, java.util.List.of()));
        UUID root = sanctum.rootObjectUuid();
        sanctum.close();

        Sanctum s2 = Sanctum.open(vault);
        s2.unlock(pw.toCharArray());

        Constructor<SanctumGui> ctor = SanctumGui.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        SanctumGui gui = ctor.newInstance();
        setField(gui, "sanctum", s2);
        setField(gui, "frame", null);
        setField(gui, "groupTree", new JTree());
        setField(gui, "entryList", new JList<>());
        setField(gui, "entryModel", new DefaultListModel<>());
        setField(gui, "statusLabel", new JLabel());

        Method performMove = SanctumGui.class.getDeclaredMethod("performMove", UUID.class, UUID.class, UUID.class);
        performMove.setAccessible(true);
        // B 拖到 A 之前（落到 A 上）：targetGroup=根，beforeUuid=A
        performMove.invoke(gui, b.uuid(), root, a.uuid());

        s2.close();
        Sanctum s3 = Sanctum.open(vault);
        s3.unlock(pw.toCharArray());
        List<UUID> order = s3.objectTree().rootEntries().stream().map(EntryNode::uuid).toList();
        s3.close();

        assertEquals(List.of(b.uuid(), a.uuid(), c.uuid()), order,
                "B 拖到 A 之前后，重开仓库顺序应为 [B, A, C]");
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = SanctumGui.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
