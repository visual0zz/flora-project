package com.flora.sanctum.app.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JComponent;
import java.awt.Component;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归测试：设置页"运行形态"按两种独立运行形态呈现不同按钮，防止被静默改错。
 * <ul>
 *   <li>普通仓库：显示「配置独立运行」。</li>
 *   <li>独立仓、但本应用并非从它启动：显示「删除独立运行」与「更新运行时版本」。</li>
 *   <li>独立仓、且本应用正从该仓自带 lib/ 启动：不显示删除/更新（避免运行时破坏自身），仅提示。</li>
 * </ul>
 * commit {@code 9bfe115c9} 曾用 {@code if (!ctx.standalone())} 把独立仓的删除入口整体隐藏，
 * 导致独立仓用户找不到删除按钮；本测试覆盖三种形态，防止再次回归。
 */
class StandaloneSettingControlTest {

    @Test
    void normalModeShowsConfigureButton() {
        JComponent c = SettingsModel.StandaloneSetting.INSTANCE.createControl(
                new SettingsModel.SettingsContext(null, null, null, false, false, () -> {}, () -> {}, () -> {}));
        assertTrue(hasButton(c, "配置独立运行"),
                "普通仓形态下应显示「配置独立运行」按钮");
    }

    @Test
    void standaloneOpenedElsewhereShowsDeleteAndUpdate() {
        JComponent c = SettingsModel.StandaloneSetting.INSTANCE.createControl(
                new SettingsModel.SettingsContext(null, null, null, true, false, () -> {}, () -> {}, () -> {}));
        assertTrue(hasButton(c, "删除独立运行"),
                "打开的独立仓（非自启动）应显示「删除独立运行」按钮");
        assertTrue(hasButton(c, "更新运行时版本"),
                "打开的独立仓（非自启动）应显示「更新运行时版本」按钮");
    }

    @Test
    void selfLaunchedHidesDestructiveButtons() {
        JComponent c = SettingsModel.StandaloneSetting.INSTANCE.createControl(
                new SettingsModel.SettingsContext(null, null, null, true, true, () -> {}, () -> {}, () -> {}));
        assertFalse(hasButton(c, "删除独立运行"),
                "从本独立仓自启动时不显示「删除独立运行」（避免自我崩溃）");
        assertFalse(hasButton(c, "更新运行时版本"),
                "从本独立仓自启动时不显示「更新运行时版本」（避免自我崩溃）");
    }

    private static boolean hasButton(JComponent root, String text) {
        AtomicBoolean found = new AtomicBoolean(false);
        walk(root, text, found);
        return found.get();
    }

    private static void walk(Component comp, String text, AtomicBoolean found) {
        if (found.get()) {
            return;
        }
        if (comp instanceof JButton b && text.equals(b.getText())) {
            found.set(true);
            return;
        }
        if (comp instanceof JComponent jc) {
            for (Component child : jc.getComponents()) {
                walk(child, text, found);
                if (found.get()) {
                    return;
                }
            }
        }
    }
}
