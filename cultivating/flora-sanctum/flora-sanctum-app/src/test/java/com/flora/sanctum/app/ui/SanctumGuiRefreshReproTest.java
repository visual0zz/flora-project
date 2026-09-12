package com.flora.sanctum.app.ui;

import com.flora.sanctum.core.model.ExternalKeyService;
import com.flora.sanctum.core.model.Sanctum;
import com.flora.sanctum.core.model.ViewNodeType;
import com.flora.sanctum.core.model.tree.EntryNode;
import com.flora.sanctum.core.model.tree.GroupNode;
import com.flora.sanctum.core.model.tree.IconNode;
import com.flora.sanctum.core.model.tree.IconTree;
import com.flora.sanctum.core.model.tree.ObjectTree;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import javax.swing.UIManager;

/**
 * 回归测试：解锁后进入主界面的刷新（真实 GUI 的 {@code buildMainPanel}→{@code refreshAll}→
 * {@code rebuildGroupTree}→{@code totpItemName}）对含 totp / 外部密钥字段的导入大仓库不得抛异常。
 * <p>曾在 {@code totpItemName}/{@code fieldStoragePath}/{@code groupIdOf} 中对"存储 hex（无连字符）"形态的
 * 字段 parent 误用 {@code UUID.fromString} 而抛 {@code IllegalArgumentException}；该异常发生在 EDT 刷新阶段，
 * 被 Swing 默认异常处理器静默吞掉，表现为"解锁转圈消失后界面无反应、且不报错"。本测试在 headless 下
 * 真实调用 GUI 构建，断言不再抛异常（回归守卫）。</p>
 */
class SanctumGuiRefreshReproTest {

    private static final byte[] PNG_1PX = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+M8AAAMBAQDJ/pLvAAAAAElFTkSuQmCC");

    @Test
    void buildMainPanelWithTotpAndExternalKeyDoesNotThrow(@TempDir Path dir) throws Exception {
        System.setProperty("java.awt.headless", "true");
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignore) {
        }

        Path vault = dir.resolve("vault");
        String pw = "test";
        Sanctum sanctum = Sanctum.createAndUnlock(vault, pw.toCharArray(), 8192, 2, 1);

        // 生成含 totp + 外部密钥字段的大仓库（贴近真实导入形态）
        ObjectTree tree = sanctum.objectTree();
        IconTree iconTree = sanctum.iconTree();
        GroupNode root = tree.createGroup(null, "导入根");
        GroupNode chain = root;
        for (int i = 0; i < 40; i++) {
            chain = chain.createChildGroup("链-" + i);
        }
        for (int host = 0; host < 2; host++) {
            GroupNode g = host == 0 ? root : chain;
            for (int i = 0; i < 30; i++) {
                EntryNode e = g.createEntry("条目-" + host + "-" + i,
                        new com.flora.sanctum.core.model.EntryFields("pw" + i, "https://x.com", "u" + i, List.of("t")));
                e.writeField("totp", "otpauth://totp/x?secret=AAA", "totp");
                IconNode ic = iconTree.createIcon("ico-" + host + "-" + i, PNG_1PX, "png");
                e.setIcon(ic.uuid());
                new ExternalKeyService(sanctum).createExternalKey(e.uuid(),
                        "ext-" + host + "-" + i, ("km-" + i).getBytes(StandardCharsets.UTF_8), "d");
            }
        }
        sanctum.close();

        // 重开并解锁（复现用户重开场景）
        Sanctum s2 = Sanctum.open(vault);
        s2.unlock(pw.toCharArray());

        // 在 headless 下真实构造 GUI，直接调用含 bug 的刷新方法（rebuildGroupTree→totpItemName、
        // refreshEntryList 默认分支→groupIdOf/folderPathOf），避开 buildMainPanel 中 headless 不支持的拖拽启用。
        Constructor<SanctumGui> ctor = SanctumGui.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        SanctumGui gui = ctor.newInstance();
        setField(gui, "sanctum", s2);
        setField(gui, "frame", null); // headless 下不可 new JFrame()，且刷新期不引用 frame
        // 初始化刷新所需的 Swing 组件（其构造在 headless 下无拖拽启用，故安全）
        setField(gui, "groupTree", new javax.swing.JTree());
        setField(gui, "entryList", new javax.swing.JList<>());
        setField(gui, "entryModel", new javax.swing.DefaultListModel<>());

        Method rebuild = SanctumGui.class.getDeclaredMethod("rebuildGroupTree");
        rebuild.setAccessible(true);
        Method refresh = SanctumGui.class.getDeclaredMethod("refreshEntryList", String.class);
        refresh.setAccessible(true);
        try {
            rebuild.invoke(gui);   // 内含 totpItemName（曾对无连字符 hex parent 抛 IllegalArgumentException）
            refresh.invoke(gui, ""); // 默认分支内含 groupIdOf/folderPathOf
        } catch (java.lang.reflect.InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            // expandRow 在 headless 下可能抛 HeadlessException（环境限制，非本 bug）；其余一律视为回归失败
            if (cause instanceof java.awt.HeadlessException) {
                return;
            }
            throw new AssertionError("解锁后主界面刷新抛异常（将被 EDT 静默吞掉的根因）", cause);
        }

        // 回归守卫：首建后"密码库"区段必须处于展开态。曾用 groupTree.getModel() == null 判断首建，
        // 而 new JTree() 自带非 null 样例模型，导致恒判为"非首建"、恢复空展开集 → 四区段全折叠，
        // 表现为重开后"密码库空了、看不到数据"。
        javax.swing.JTree jtree = (javax.swing.JTree) getField(gui, "groupTree");
        javax.swing.tree.DefaultMutableTreeNode rootNode =
                (javax.swing.tree.DefaultMutableTreeNode)
                        ((javax.swing.tree.DefaultTreeModel) jtree.getModel()).getRoot();
        boolean passwordExpanded = false;
        java.util.Enumeration<?> expanded =
                jtree.getExpandedDescendants(new javax.swing.tree.TreePath(rootNode));
        if (expanded != null) {
            while (expanded.hasMoreElements()) {
                javax.swing.tree.TreePath p = (javax.swing.tree.TreePath) expanded.nextElement();
                if (((javax.swing.tree.DefaultMutableTreeNode) p.getLastPathComponent()).getUserObject()
                        == ViewNodeType.PASSWORD) {
                    passwordExpanded = true;
                }
            }
        }
        assertTrue(passwordExpanded, "首建后密码库区段应默认展开，否则左树看起来一片空白");

        // 回归守卫：全量搜索必须能命中导入的条目（搜索走 objectTree().nodes()，与树展开状态无关）
        javax.swing.DefaultListModel<?> listModel =
                (javax.swing.DefaultListModel<?>) getField(gui, "entryModel");
        refresh.invoke(gui, "条目");
        assertTrue(listModel.size() > 0, "全量搜索应能命中导入的条目");

        // 回归守卫：根隐藏时必须开启 showsRootHandles，否则顶层四个区段没有展开三角，
        // 折叠后看似"没有数据"（用户把折叠误判为空库的直接原因）。
        Method configure = SanctumGui.class.getDeclaredMethod("configureGroupTree", javax.swing.JTree.class);
        configure.setAccessible(true);
        javax.swing.JTree probe = new javax.swing.JTree();
        configure.invoke(null, probe);
        assertTrue(probe.getShowsRootHandles(), "根隐藏时区段节点必须显示展开柄");
    }

    private static Object getField(Object target, String name) throws Exception {
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
