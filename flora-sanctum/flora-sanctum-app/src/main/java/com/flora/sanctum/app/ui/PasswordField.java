package com.flora.sanctum.app.ui;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;

/**
 * 统一的密码输入控件：左侧编辑框 + 右侧眼睛图标，点击在明文/密文间切换。
 * <p>对外暴露与 {@link JPasswordField} 对齐的访问方法（getText / getPassword / setText /
 * setColumns / addActionListener / addDocumentListener / requestFocusInWindow），以便无缝替换
 * 解锁、新建、条目编辑、导入等处的裸 {@link JPasswordField}。所有密码输入 UI 共用此组件，
 * 视觉与交互一致。</p>
 */
public final class PasswordField extends JPanel {

    private static final char HIDDEN = '\u2022';

    private final JPasswordField field;
    private final JButton eye;
    private boolean revealed = false;

    public PasswordField(int columns) {
        super(new BorderLayout(4, 0));
        field = new JPasswordField(columns);
        field.setEchoChar(HIDDEN);
        eye = new JButton(SvgIcon.get(UiIcon.EYE_OFF, 18));
        eye.setToolTipText("显示/隐藏密码");
        eye.setBorderPainted(false);
        eye.setContentAreaFilled(false);
        eye.setFocusPainted(false);
        eye.setMargin(new Insets(2, 2, 2, 2));
        eye.addActionListener(e -> toggle());
        add(field, BorderLayout.CENTER);
        add(eye, BorderLayout.EAST);
    }

    private void toggle() {
        revealed = !revealed;
        field.setEchoChar(revealed ? (char) 0 : HIDDEN);
        eye.setIcon(SvgIcon.get(revealed ? UiIcon.EYE : UiIcon.EYE_OFF, 18));
    }

    /** 密码明文（无论是否遮蔽，均返回真实字符）。 */
    public char[] getPassword() {
        return field.getPassword();
    }

    /** 密码明文（字符串形式，便于 GUI 读取）。 */
    public String getText() {
        return field.getText();
    }

    public void setText(String text) {
        field.setText(text == null ? "" : text);
    }

    public void setColumns(int columns) {
        field.setColumns(columns);
    }

    public void setEditable(boolean editable) {
        field.setEditable(editable);
    }

    @Override
    public void setEnabled(boolean enabled) {
        field.setEnabled(enabled);
        eye.setEnabled(enabled);
        super.setEnabled(enabled);
    }

    public void addActionListener(ActionListener l) {
        field.addActionListener(l);
    }

    public void addDocumentListener(DocumentListener l) {
        field.getDocument().addDocumentListener(l);
    }

    @Override
    public boolean requestFocusInWindow() {
        return field.requestFocusInWindow();
    }

    /** 底层编辑框（极少需要）。 */
    public JPasswordField textField() {
        return field;
    }
}
