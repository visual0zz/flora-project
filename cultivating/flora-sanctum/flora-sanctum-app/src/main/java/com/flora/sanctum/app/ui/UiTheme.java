package com.flora.sanctum.app.ui;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.LayoutManager;
import java.util.Random;

/**
 * flora-sanctum 统一 UI 主题（配色随方案切换）。
 * <p>
 * 支持三套方案（见 {@link #applyScheme(String)}）：
 * <ul>
 *   <li>{@code light}：暖纸纯色风格（参考 openhanako Warm Paper）——默认方案。
 *   <li>{@code dark}：暗色方案（深色底、浅色文字）。
 *   <li>{@code stupid}：所有位置颜色在应用主题时随机生成（每次应用都不同，仅供娱乐）。
 * </ul>
 * 配色为可变静态字段：UI 组件在绘制时读取（如 {@link PaperPanel}），切换方案后重绘即生效；
 * 方案名存于全局配置文件（见 {@code com.flora.sanctum.app.config.UserConfig#theme()}）。
 */
public final class UiTheme {

    private UiTheme() {
    }

    /** 面板底色。 */
    public static Color PAPER;
    /** 控件/工具栏底色（与背景略有区分）。 */
    public static Color PAPER_LIGHT;
    /** 编辑框底色。 */
    public static Color FIELD_WHITE;
    /** 卡片底色。 */
    public static Color CARD;
    /** 主色。 */
    public static Color ACCENT;
    /** 文字色。 */
    public static Color INK;
    /** 弱文字色。 */
    public static Color INK_MUTED;
    /** 分隔线。 */
    public static Color DIVIDER;
    /** 选中背景。 */
    public static Color SELECTION_BG;

    static {
        applyLight();
        apply();
    }

    /** 应用配色方案（light / dark / stupid；未知值回落 light）。 */
    public static void applyScheme(String scheme) {
        switch (scheme == null ? "light" : scheme) {
            case "dark" -> applyDark();
            case "stupid" -> applyStupid();
            default -> applyLight();
        }
        apply();
    }

    /** light：暖纸风格。 */
    private static void applyLight() {
        PAPER = new Color(0xF8, 0xF5, 0xED);
        PAPER_LIGHT = new Color(0xF4, 0xF2, 0xEA);
        FIELD_WHITE = new Color(0xFF, 0xFF, 0xFF);
        CARD = new Color(0xFC, 0xFA, 0xF5);
        ACCENT = new Color(0x53, 0x7D, 0x96);
        INK = new Color(0x3B, 0x3D, 0x3F);
        INK_MUTED = new Color(0x8E, 0x91, 0x96);
        DIVIDER = new Color(0xD4, 0xDA, 0xDA);
        SELECTION_BG = new Color(0xE8, 0xEE, 0xF2);
    }

    /** dark：深色底 + 浅色文字。 */
    private static void applyDark() {
        PAPER = new Color(0x2A, 0x2C, 0x30);
        PAPER_LIGHT = new Color(0x33, 0x36, 0x3B);
        FIELD_WHITE = new Color(0x1F, 0x21, 0x24);
        CARD = new Color(0x31, 0x34, 0x38);
        ACCENT = new Color(0x7F, 0xB3, 0xD0);
        INK = new Color(0xE4, 0xE6, 0xE8);
        INK_MUTED = new Color(0x9C, 0xA0, 0xA6);
        DIVIDER = new Color(0x44, 0x48, 0x4E);
        SELECTION_BG = new Color(0x3E, 0x48, 0x52);
    }

    /** stupid：所有位置的颜色临时随机生成。 */
    private static void applyStupid() {
        PAPER = randomColor();
        PAPER_LIGHT = randomColor();
        FIELD_WHITE = randomColor();
        CARD = randomColor();
        ACCENT = randomColor();
        INK = randomColor();
        INK_MUTED = randomColor();
        DIVIDER = randomColor();
        SELECTION_BG = randomColor();
    }

    private static Color randomColor() {
        Random r = new Random();
        return new Color(r.nextInt(256), r.nextInt(256), r.nextInt(256));
    }

    /** 将当前配色写入 UIManager（对 Swing 控件生效）。 */
    public static void apply() {
        javax.swing.UIManager.put("Panel.background", PAPER);
        javax.swing.UIManager.put("Panel.foreground", INK);
        javax.swing.UIManager.put("Label.foreground", INK);
        javax.swing.UIManager.put("Component.background", PAPER);
        javax.swing.UIManager.put("Component.foreground", INK);
        javax.swing.UIManager.put("TextField.background", FIELD_WHITE);
        javax.swing.UIManager.put("TextField.foreground", INK);
        javax.swing.UIManager.put("TextField.caretForeground", INK);
        javax.swing.UIManager.put("TextArea.background", FIELD_WHITE);
        javax.swing.UIManager.put("TextArea.foreground", INK);
        javax.swing.UIManager.put("PasswordField.background", FIELD_WHITE);
        javax.swing.UIManager.put("PasswordField.foreground", INK);
        javax.swing.UIManager.put("Tree.background", PAPER);
        javax.swing.UIManager.put("Tree.foreground", INK);
        javax.swing.UIManager.put("Tree.selectionBackground", SELECTION_BG);
        javax.swing.UIManager.put("Tree.selectionInactiveBackground", SELECTION_BG);
        javax.swing.UIManager.put("Tree.selectionForeground", INK);
        javax.swing.UIManager.put("Tree.textBackground", PAPER);
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
        javax.swing.UIManager.put("CheckBox.background", PAPER);
        javax.swing.UIManager.put("CheckBox.foreground", INK);
        javax.swing.UIManager.put("RadioButton.background", PAPER);
        javax.swing.UIManager.put("TextComponent.selectionBackground", SELECTION_BG);
        javax.swing.UIManager.put("TextComponent.selectionForeground", INK);
        javax.swing.UIManager.put("Label.disabledForeground", INK_MUTED);
        javax.swing.UIManager.put("Button.disabledText", INK_MUTED);
    }

    /**
     * 纯色背景面板：各界面根面板统一用此组件保证背景一致。
     * 默认纸感底色（{@link #PAPER}）；可自定义底色（如卡片 {@link #CARD}）。
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
