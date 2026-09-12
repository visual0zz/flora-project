package com.flora.sanctum.app.ui;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * 多行备注编辑控件：正文 + 右上角眼睛图标，点击在明文/密文之间切换。
 * 默认以圆点遮蔽，避免敏感内容（SSH 私钥、备注等）在编辑界面直接暴露。
 * <p>遮罩在渲染层完成：编辑框文档始终保存真实文本，{@link MaskArea#paintComponent} 在 UI 绘制后
 * 用背景色抹掉可见文字并逐字画圆点。因此遮罩/显示切换不涉及任何内容改写，
 * 反复点击始终稳定，{@link #getText()} 无论在何种状态都返回真实内容。</p>
 */
final class MaskedNotesArea extends JPanel {

    private final MaskArea area;
    private final JButton eye;
    private boolean revealed = false;

    MaskedNotesArea(String value) {
        super(new BorderLayout(0, 0));
        area = new MaskArea(value == null ? "" : value);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        eye = eyeButton();
        eye.addActionListener(e -> toggle());
        JPanel eyeBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        eyeBar.setOpaque(false);
        eyeBar.add(eye);
        add(eyeBar, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        setMasked(true);
    }

    private static JButton eyeButton() {
        JButton b = new JButton(SvgIcon.get(UiIcon.EYE_OFF, 18));
        b.setToolTipText("显示/隐藏备注");
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setMargin(new Insets(2, 2, 2, 2));
        return b;
    }

    private void toggle() {
        setMasked(revealed);
    }

    private void setMasked(boolean masked) {
        area.setMasked(masked);
        area.setEditable(!masked);
        // 遮罩态不可获得焦点、清空选区，避免真实内容被选中/复制
        area.setFocusable(!masked);
        if (masked) {
            area.select(0, 0);
        }
        eye.setIcon(SvgIcon.get(masked ? UiIcon.EYE_OFF : UiIcon.EYE, 18));
        revealed = !masked;
    }

    /** 备注明文（无论遮罩与否均返回真实内容）。 */
    String getText() {
        return area.getText();
    }

    void setText(String text) {
        area.setText(text == null ? "" : text);
    }

    @Override
    public void setEnabled(boolean enabled) {
        area.setEnabled(enabled);
        eye.setEnabled(enabled);
        super.setEnabled(enabled);
    }

    /**
     * 文档只保存真实文本；遮罩态在 {@link #paintComponent} 里覆盖绘制，不修改文档。
     */
    private static final class MaskArea extends JTextArea {

        private boolean masked = false;

        MaskArea(String text) {
            super(text);
        }

        void setMasked(boolean masked) {
            if (this.masked == masked) {
                return;
            }
            this.masked = masked;
            repaint();
        }

        @Override
        public void copy() {
            if (!masked) {
                super.copy();
            }
        }

        @Override
        public void cut() {
            if (!masked) {
                super.cut();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (!masked) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                paintMask(g2);
            } finally {
                g2.dispose();
            }
        }

        /** 用背景色抹掉可见范围内的真实文字（含选中高亮），再按字符位置逐字画圆点。 */
        private void paintMask(Graphics2D g) {
            Rectangle clip = g.getClipBounds();
            if (clip == null) {
                clip = new Rectangle(0, 0, getWidth(), getHeight());
            }
            Color textColor = getForeground();
            g.setColor(getBackground());
            g.fillRect(clip.x, clip.y, clip.width, clip.height);

            int dot = Math.max(3, getFont().getSize() / 4);
            g.setColor(textColor);
            int len = getDocument().getLength();
            int start = Math.max(0, viewToModel2D(new Point2D.Float(clip.x, clip.y)) - 1);
            for (int i = start; i < len; i++) {
                Rectangle2D r;
                try {
                    r = modelToView2D(i);
                } catch (BadLocationException e) {
                    break;
                }
                if (r == null) {
                    continue;
                }
                if (r.getMinY() > clip.getMaxY()) {
                    break;
                }
                if (r.getMaxY() < clip.getMinY()) {
                    continue;
                }
                try {
                    if (getDocument().getText(i, 1).charAt(0) == '\n') {
                        continue;
                    }
                } catch (BadLocationException e) {
                    continue;
                }
                int x = (int) r.getX() + ((int) r.getWidth() - dot) / 2;
                int y = (int) r.getY() + ((int) r.getHeight() - dot) / 2;
                g.fillOval(x, y, dot, dot);
            }
        }
    }
}
