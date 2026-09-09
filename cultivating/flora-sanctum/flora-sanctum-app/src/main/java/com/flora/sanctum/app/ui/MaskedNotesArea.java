package com.flora.sanctum.app.ui;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

/**
 * 多行备注编辑控件：正文 + 右上角眼睛图标，点击在明文/密文之间切换。
 * 默认以圆点遮蔽，避免敏感备注在编辑界面直接暴露；点击眼睛后显示真实内容并可编辑。
 * 与 {@link PasswordField} 交互一致，但支持多行换行。
 */
final class MaskedNotesArea extends JPanel {

    private static final char HIDDEN = '•';

    private final JTextArea area;
    private final JButton eye;
    private boolean revealed = false;
    private String real;

    MaskedNotesArea(String value) {
        super(new BorderLayout(0, 0));
        real = value == null ? "" : value;
        area = new JTextArea(real);
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
        area.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                sync();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                sync();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                sync();
            }

            private void sync() {
                if (revealed) {
                    real = area.getText();
                }
            }
        });
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

    private void setMasked(boolean masked) {
        if (masked) {
            area.setText(masked(real));
            area.setEditable(false);
            eye.setIcon(SvgIcon.get(UiIcon.EYE_OFF, 18));
            revealed = false;
        } else {
            area.setText(real);
            area.setEditable(true);
            eye.setIcon(SvgIcon.get(UiIcon.EYE, 18));
            revealed = true;
        }
    }

    private void toggle() {
        if (revealed) {
            real = area.getText();
            setMasked(true);
        } else {
            setMasked(false);
        }
    }

    private static String masked(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(c == '\n' ? '\n' : HIDDEN);
        }
        return sb.toString();
    }

    /** 备注明文（无论遮罩与否均返回真实内容）。 */
    String getText() {
        return real;
    }

    void setText(String text) {
        real = text == null ? "" : text;
        if (revealed) {
            area.setText(real);
        } else {
            area.setText(masked(real));
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        area.setEnabled(enabled);
        eye.setEnabled(enabled);
        super.setEnabled(enabled);
    }
}
