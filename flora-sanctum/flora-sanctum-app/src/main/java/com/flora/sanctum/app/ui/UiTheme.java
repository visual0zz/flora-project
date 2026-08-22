package com.flora.sanctum.app.ui;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.LayoutManager;
import java.awt.image.BufferedImage;

/**
 * flora-sanctum 统一 UI 主题（白灰偏暖黄纸感风格）。
 * <p>
 * 供所有界面（解锁页、编辑页、历史列表页、设置页等）共用，保证配色与透明方案一致：
 * 纸感底纹背景（PaperPanel）+ 暖浅控件底色 + 纯白输入框 + 淡灰棕文字/分隔线。
 */
public final class UiTheme {

    private UiTheme() {
    }

    /** 面板底色（暖白纸）。 */
    public static final Color PAPER = new Color(0xF8, 0xF4, 0xE9);
    /** 按钮/下拉等控件底色（暖浅）。 */
    public static final Color PAPER_LIGHT = new Color(0xF1, 0xED, 0xE1);
    /** 编辑框纯白。 */
    public static final Color FIELD_WHITE = new Color(0xFF, 0xFF, 0xFF);
    /** 文字图标淡灰棕。 */
    public static final Color INK = new Color(0x5A, 0x55, 0x4D);
    /** 分隔线。 */
    public static final Color DIVIDER = new Color(0xD8, 0xD2, 0xC0);
    /** 选中背景（暖灰棕，焦点/失焦一致）。 */
    public static final Color SELECTION_BG = new Color(0xE4, 0xDD, 0xC9);

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
     * 纸感底纹背景面板：确定性平滑值噪声按面板尺寸整体渲染（函数全局连续，平铺无接缝）。
     * 各界面根面板统一用此组件，保证背景一致。
     */
    public static class PaperPanel extends JPanel {
        private final int baseR;
        private final int baseG;
        private final int baseB;
        private final int amp;
        private BufferedImage cached;
        private int cachedW = -1;
        private int cachedH = -1;

        public PaperPanel(LayoutManager layout) {
            this(layout, 0xF8, 0xF4, 0xE9, 25);
        }

        /** 自定义基色与幅度的纸纹面板（如卡片用更深基色）。 */
        public PaperPanel(LayoutManager layout, int baseR, int baseG, int baseB, int amp) {
            super(layout);
            this.baseR = baseR;
            this.baseG = baseG;
            this.baseB = baseB;
            this.amp = amp;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            if (cached == null || cachedW != w || cachedH != h) {
                cached = renderPaper(w, h);
                cachedW = w;
                cachedH = h;
            }
            g.drawImage(cached, 0, 0, null);
        }

        /** 按面板尺寸渲染纸纤维噪声图（复用 flora-root PaperNoise，基色可自定义，默认暖白 #F8F4E9 幅度 ±25）。 */
        private BufferedImage renderPaper(int w, int h) {
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            int[] px = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    float n = com.flora.root.graphics.noise.PaperNoise.paper(x, y);
                    int d = (int) Math.round(n * amp);
                    int r = com.flora.root.graphics.noise.PaperNoise.clamp(baseR + d);
                    int g = com.flora.root.graphics.noise.PaperNoise.clamp(baseG + d);
                    int b = com.flora.root.graphics.noise.PaperNoise.clamp(baseB + d);
                    px[y * w + x] = (r << 16) | (g << 8) | b;
                }
            }
            img.setRGB(0, 0, w, h, px, 0, w);
            return img;
        }
    }
}
