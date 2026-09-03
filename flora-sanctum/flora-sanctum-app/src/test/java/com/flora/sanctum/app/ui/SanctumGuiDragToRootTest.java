package com.flora.sanctum.app.ui;

import com.flora.sanctum.core.model.Sanctum;
import com.flora.sanctum.core.model.tree.GroupNode;
import com.flora.sanctum.core.model.tree.ObjectTree;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.UUID;

import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.DefaultListModel;

/**
 * 回归测试：拖拽组到左树「密码库」根（targetGroup=null）应将其移到顶层。
 * <p>曾因 {@code SanctumGui.performMove} 以 {@code targetGroup == null} 提前 return 而把整类操作静默吞掉，
 * 表现为「无法拖动到密码库」：拖到根看似落下却毫无效果。修复后 null 代表顶层（NodeMover 写入根对象），
 * 故此处断言子组拖到根后成为顶层组。</p>
 */
class SanctumGuiDragToRootTest {

    @Test
    void dragGroupToVaultRootMovesToTopLevel(@TempDir Path dir) throws Exception {
        System.setProperty("java.awt.headless", "true");
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignore) {
        }

        Path vault = dir.resolve("vault");
        String pw = "test";
        Sanctum sanctum = Sanctum.createAndUnlock(vault, pw.toCharArray(), 8192, 2, 1);
        ObjectTree tree = sanctum.objectTree();
        GroupNode root = tree.createGroup(null, "根");
        GroupNode sub = root.createChildGroup("子组"); // 嵌套在「根」之下，非顶层
        UUID subId = sub.uuid();
        sanctum.close();

        Sanctum s2 = Sanctum.open(vault);
        s2.unlock(pw.toCharArray());

        // headless 下真实构造 GUI 并调用 performMove（拖到根 = targetGroup=null）
        Constructor<SanctumGui> ctor = SanctumGui.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        SanctumGui gui = ctor.newInstance();
        setField(gui, "sanctum", s2);
        setField(gui, "frame", null);
        setField(gui, "groupTree", new JTree());
        setField(gui, "entryList", new JList<>());
        setField(gui, "entryModel", new DefaultListModel<>());
        // statusLabel 必须设置，否则 performMove 的 catch 分支 setText 会 NPE 并掩盖真实结果
        setField(gui, "statusLabel", new JLabel());

        Method performMove = SanctumGui.class.getDeclaredMethod("performMove", UUID.class, UUID.class);
        performMove.setAccessible(true);
        try {
            // targetGroup=null 表示落到「密码库」区段（顶层）
            performMove.invoke(gui, subId, (UUID) null);
        } catch (ReflectiveOperationException ex) {
            Throwable cause = ex.getCause();
            // refreshAll 在 headless 下可能因 Swing 组件未真正显示而抛 HeadlessException（环境限制，非本 bug）；
            // 移动本身在 refreshAll 之前已执行，不影响断言。其余异常一律视为回归失败。
            if (!(cause instanceof java.awt.HeadlessException)) {
                throw new AssertionError("performMove(组, null) 抛异常", cause);
            }
        }

        // 重开仓库，用全新的 ObjectTree 断言子组已成为顶层组（parent=根对象）
        s2.close();
        Sanctum s3 = Sanctum.open(vault);
        s3.unlock(pw.toCharArray());
        boolean isRootGroup = s3.objectTree().rootGroups().stream()
                .anyMatch(g -> g.uuid().equals(subId));
        s3.close();

        org.junit.jupiter.api.Assertions.assertTrue(isRootGroup,
                "拖到密码库根后，子组应成为顶层组（当前仍嵌套在其它组之下）");
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = SanctumGui.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
