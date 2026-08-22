package com.flora.sanctum.app.ui;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.LayoutManager;

/**
 * flora-sanctum 统一 UI 主题（暖纸纯色风格，参考 openhanako Warm Paper）。
 * <p>
 * 供所有界面（解锁页、编辑页、历史列表页、设置页等）共用，保证配色一致：
 * 纯色背景（PaperPanel）+ 暖浅控件底色 + 纯白输入框 + 深灰文字/浅蓝分隔线。
 */
public final class UiTheme {

    private UiTheme() {
    }

    /** 面板底色（暖纸，参考 openhanako Warm Paper --bg）。 */
    public static final Color PAPER = new Color(0xF8, 0xF5, 0xED);
    /** 控件/工具栏底色（略深，--sidebar-bg / --bg-glass 取向）。 */
    public static final Color PAPER_LIGHT = new Color(0xF4, 0xF2, 0xEA);
    /** 编辑框纯白。 */
    public static final Color FIELD_WHITE = new Color(0xFF, 0xFF, 0xFF);
    /** 卡片底色（比背景更白，--bg-card）。 */
    public static final Color CARD = new Color(0xFC, 0xFA, 0xF5);
    /** 主色（蓝灰，--accent）。 */
    public static final Color ACCENT = new Color(0x53, 0x7D, 0x96);
    /** 文字（深灰，--text）。 */
    public static final Color INK = new Color(0x3B, 0x3D, 0x3F);
    /** 弱文字（--text-muted）。 */
    public static final Color INK_MUTED = new Color(0x8E, 0x91, 0x96);
    /** 分隔线（--border 蓝调浅色，在 PAPER 上合成）。 */
    public static final Color DIVIDER = new Color(0xD4, 0xDA, 0xDA);
    /** 选中背景（--accent-light 淡蓝，焦点/失焦一致）。 */
    public static final Color SELECTION_BG = new Color(0xE8, 0xEE, 0xF2);

    /** 应用全局 UIManager 纸感主题（对 Swing 控件生效）。 */
    public static void apply() {
        javax.swing.UIManager.put("Panel.background", PAPER);
        javax.swing.UIManager.put("Panel.foreground", INK);
        javax.swing.UIManager.put("Label.foreground", INK);
        javax.swing.UIManager.put("Component.background", PAPER);
        javax.swing.UIManager.put("Component.foreground", INK);
        javax.swing.UIManager.put("TextField.background", FIELD_WHITE);
        javax.swing.UIManager.put("TextField.foreground", INK);
        javax.swing.UIManager.put("TextArea.background", FIELD_WHITE);
        javax.swing.UIManager.put("TextArea.foreground", INK);
        javax.swing.UIManager.put("PasswordField.background", FIELD_WHITE);
        javax.swing.UIManager.put("PasswordField.foreground", INK);
        javax.swing.UIManager.put("Tree.background", PAPER);
        javax.swing.UIManager.put("Tree.foreground", INK);
        javax.swing.UIManager.put("Tree.selectionBackground", SELECTION_BG);
        javax.swing.UIManager.put("Tree.selectionInactiveBackground", SELECTION_BG);
        javax.swing.UIManager.put("Tree.selectionForeground", INK);
        javax.swing.UIManager.put("List.background", PAPER);
        javax.swing.UIManager.put("List.foreground", INK);
        javax.swing.UIManager.put("List.selectionBackground", SELECTION_BG);
        javax.swing.UIManager.put("List.selectionInactiveBackground", SELECTION_BG);
        javax.swing.UIManager.put("List.selectionForeground", INK);
        javax.swing.UIManager.put("Table.background", PAPER);
        javax.swing.UIManager.put("Table.foreground", INK);
        javax.swing.UIManager.put("Table.selectionBackground", SELECTION_BG);
        javax.swing.UIManager.put("Table.selectionInactiveBackground", SELECTION_BG);
        javax.swing.UIManager.put("Table.selectionForeground", INK);
        javax.swing.UIManager.put("Viewport.background", PAPER);
        javax.swing.UIManager.put("ScrollPane.background", PAPER);
        javax.swing.UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());
        javax.swing.UIManager.put("Button.background", PAPER_LIGHT);
        javax.swing.UIManager.put("Button.foreground", INK);
        javax.swing.UIManager.put("ComboBox.background", PAPER_LIGHT);
        javax.swing.UIManager.put("ComboBox.foreground", INK);
        javax.swing.UIManager.put("Spinner.background", PAPER_LIGHT);
        javax.swing.UIManager.put("Spinner.foreground", INK);
        javax.swing.UIManager.put("ToolBar.background", PAPER);
        javax.swing.UIManager.put("ToolBar.border", BorderFactory.createEmptyBorder());
        javax.swing.UIManager.put("SplitPane.dividerSize", 1);
        javax.swing.UIManager.put("SplitPane.background", DIVIDER);
        javax.swing.UIManager.put("SplitPaneDivider.border", BorderFactory.createLineBorder(DIVIDER));
        javax.swing.UIManager.put("TitledBorder.border", BorderFactory.createLineBorder(DIVIDER));
        javax.swing.UIManager.put("TitledBorder.titleColor", INK);
        javax.swing.UIManager.put("TableHeader.background", PAPER_LIGHT);
        javax.swing.UIManager.put("TableHeader.foreground", INK);
    }

    /**
     * 纯色背景面板：各界面根面板统一用此组件保证背景一致。
     * 默认暖纸底色（{@link #PAPER}）；可自定义底色（如卡片 {@link #CARD}）。
     */
    public static class PaperPanel extends JPanel {

        public PaperPanel(LayoutManager layout) {
            this(layout, PAPER);
        }

        public PaperPanel(LayoutManager layout, Color bg) {
            super(layout);
            setOpaque(true);
            setBackground(bg);
        }
    }
}
