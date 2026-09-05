package com.flora.sanctum.app.ui;

import com.flora.sanctum.core.model.EntryFields;
import com.flora.sanctum.core.model.Ref;
import com.flora.sanctum.core.model.Sanctum;
import com.flora.sanctum.core.model.tree.EntryNode;
import com.flora.sanctum.core.model.tree.IconNode;
import com.flora.sanctum.core.model.tree.IconTree;
import com.flora.sanctum.core.model.tree.ObjectTree;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.UIManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 回归测试：左/中栏自定义图标渲染。
 * <p>曾因 {@code iconById} 对 node 引用用 {@code UUID.fromString(ref.id())} 解析——而 {@code ref.id()}
 * 是 32 位无连字符 hex（{@link Ref#node(String, java.util.UUID)} 经 {@code UuidHex.toHex} 生成），
 * 与 {@code UUID.fromString} 只认 36 位标准形式冲突，抛 {@code IllegalArgumentException} 被
 * {@code catch} 静默吞掉，于是所有自定义图标（无论 SVG 与否）一律回落默认图标。</p>
 * <p>本测试真实构造 GUI 并直接调用 {@code iconById}，断言自定义 PNG（用户导入的主要形态）与 SVG 图标
 * 均能解析为非空 Icon；同时断言内置图标引用不受影响。</p>
 */
class CustomIconRenderingTest {

    private static final byte[] PNG_1PX = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+M8AAAMBAQDJ/pLvAAAAAElFTkSuQmCC");

    private static final byte[] SVG_1PX = (
            "<?xml version=\"1.0\"?>\n" +
            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\">\n" +
            "  <rect width=\"24\" height=\"24\" fill=\"#537D96\"/>\n" +
            "</svg>\n").getBytes(StandardCharsets.UTF_8);

    @Test
    void customPngIconResolvesToNonNullIcon(@TempDir Path dir) throws Exception {
        Holder h = guiWithCustomIcon(dir, "logo.png", PNG_1PX, "png");
        Method iconById = SanctumGui.class.getDeclaredMethod("iconById", Ref.class, int.class);
        iconById.setAccessible(true);
        Icon ic = (Icon) iconById.invoke(h.gui(), h.ref(), 24);
        assertNotNull(ic, "自定义 PNG 图标应经 iconById 解析为非空 Icon（修复前因 UUID.fromString(32位hex) 抛异常回落默认图标）");
    }

    @Test
    void customSvgIconResolvesToNonNullIcon(@TempDir Path dir) throws Exception {
        Holder h = guiWithCustomIcon(dir, "logo.svg", SVG_1PX, "svg");
        Method iconById = SanctumGui.class.getDeclaredMethod("iconById", Ref.class, int.class);
        iconById.setAccessible(true);
        Icon ic = (Icon) iconById.invoke(h.gui(), h.ref(), 24);
        assertNotNull(ic, "自定义 SVG 图标应经 iconById 解析为非空 Icon");
    }

    @Test
    void builtinIconStillResolves(@TempDir Path dir) throws Exception {
        System.setProperty("java.awt.headless", "true");
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignore) {
        }
        Path vault = dir.resolve("vault");
        Sanctum s = Sanctum.createAndUnlock(vault, "pw".toCharArray(), 8192, 2, 1);
        s.close();
        Sanctum s2 = Sanctum.open(vault);
        s2.unlock("pw".toCharArray());
        SanctumGui gui = newGui(s2);
        Method iconById = SanctumGui.class.getDeclaredMethod("iconById", Ref.class, int.class);
        iconById.setAccessible(true);
        Icon ic = (Icon) iconById.invoke(gui, Ref.builtinIcon("48-folder"), 24);
        assertNotNull(ic, "内置图标引用应正常解析");
        assertInstanceOf(ImageIcon.class, ic);
        s2.close();
    }

    // 构造一个含自定义图标的库，并把该图标设为某条目的图标；返回可直接调用 iconById 的 GUI 与对应 Ref
    private static Holder guiWithCustomIcon(@TempDir Path dir, String name, byte[] data, String format) throws Exception {
        System.setProperty("java.awt.headless", "true");
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignore) {
        }
        Path vault = dir.resolve("vault");
        Sanctum s = Sanctum.createAndUnlock(vault, "pw".toCharArray(), 8192, 2, 1);
        ObjectTree tree = s.objectTree();
        IconTree iconTree = s.iconTree();
        IconNode custom = iconTree.createIcon(name, data, format);
        EntryNode entry = tree.createEntry(null, "条目", new EntryFields("p", null, "u", List.of()));
        entry.setIcon(custom.uuid());
        Ref ref = s.objectTree().entry(entry.uuid()).iconRef();
        s.close();

        Sanctum s2 = Sanctum.open(vault);
        s2.unlock("pw".toCharArray());
        return new Holder(newGui(s2), ref);
    }

    private static SanctumGui newGui(Sanctum s) throws Exception {
        Constructor<SanctumGui> ctor = SanctumGui.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        SanctumGui gui = ctor.newInstance();
        setField(gui, "sanctum", s);
        setField(gui, "frame", null);
        setField(gui, "groupTree", new javax.swing.JTree());
        setField(gui, "entryList", new javax.swing.JList<>());
        setField(gui, "entryModel", new javax.swing.DefaultListModel<>());
        return gui;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = SanctumGui.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private record Holder(SanctumGui gui, Ref ref) {
    }
}
