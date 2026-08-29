package com.flora.sanctum.app.ui;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.Box;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

/**
 * 新建仓库对话框（模仿 IDEA 新建项目向导）：统一设置仓库名与位置，实时展示最终路径，
 * 下方常驻「高级」设置区（勾选独立仓库 + 嵌套的 Argon2id 强度）。
 * <p>本类只负责收集与校验输入，仓库的实际创建（普通/独立）由 {@code onConfirm} 回调完成，
 * 以便复用应用级配置构建逻辑。</p>
 */
final class NewVaultDialog extends JDialog {

    /** 收集结果：最终仓库目录、是否独立仓库、KDF 参数（null = 默认档）、输入密码（明文 char[]，调用方消费后清零）。 */
    record Request(Path target, boolean standalone, int[] kdf, char[] password) {
    }

    /** 名称为空时的占位名（用于输入框占位符与最终路径渲染）。 */
    private static final String NAME_PLACEHOLDER = "新密码仓库";

    private final PlaceholderField nameField = new PlaceholderField(20, NAME_PLACEHOLDER);
    private final JTextField locationField = new JTextField(28);
    private final JLabel finalPathLabel = new JLabel();
    private final JPanel advancedPanel = new JPanel();
    private final JCheckBox standaloneCheck = new JCheckBox("独立仓库", false);
    private final KdfParamsPanel kdfPanel = new KdfParamsPanel();
    private final JPasswordField pwField = new JPasswordField(20);
    private final JPasswordField confirmField = new JPasswordField(20);
    private final JLabel strengthLabel = new JLabel("", JLabel.LEFT);
    private final JLabel mismatchLabel = new JLabel("", JLabel.LEFT);
    private final JLabel errorLabel = new JLabel("", JLabel.LEFT);

    private final Consumer<Request> onConfirm;

    NewVaultDialog(java.awt.Window owner, Consumer<Request> onConfirm) {
        super(owner, "新建仓库", ModalityType.APPLICATION_MODAL);
        this.onConfirm = onConfirm;

        locationField.setText(defaultLocation().toString());
        setLayout(new BorderLayout(0, 0));
        add(buildForm(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        // 高级设置常驻显示；finalPath 跟随默认位置
        updateFinalPath();

        setMinimumSize(new Dimension(460, 360));
        pack();
        setLocationRelativeTo(owner);
    }

    private JPanel buildForm() {
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(14, 16, 6, 16));
        form.setOpaque(false);

        form.add(labeledRow("名称：", nameField));
        form.add(Box.createVerticalStrut(10));

        JPanel locRow = new JPanel(new BorderLayout(8, 0));
        locRow.setOpaque(false);
        locRow.add(new JLabel("位置："), BorderLayout.WEST);
        JButton browseBtn = new JButton("浏览…");
        browseBtn.addActionListener(e -> chooseLocation());
        JPanel locFieldRow = new JPanel(new BorderLayout(8, 0));
        locFieldRow.setOpaque(false);
        locFieldRow.add(locationField, BorderLayout.CENTER);
        locFieldRow.add(browseBtn, BorderLayout.EAST);
        locRow.add(locFieldRow, BorderLayout.CENTER);
        form.add(locRow);
        form.add(Box.createVerticalStrut(6));

        // 实时最终路径提示（模仿 IDEA "Project will be created at"）：占满宽度、文本靠左（随窗口拉伸）
        finalPathLabel.setForeground(new Color(0x8E, 0x91, 0x96));
        finalPathLabel.setHorizontalAlignment(SwingConstants.LEFT);
        form.add(finalPathLabel);
        form.add(Box.createVerticalStrut(10));

        // 输入密码（新建即在创建页设定，避免后续再弹解锁页）
        form.add(labeledRow("输入密码：", pwField));
        form.add(Box.createVerticalStrut(4));
        form.add(labeledRow("确认密码：", confirmField));
        form.add(Box.createVerticalStrut(2));
        mismatchLabel.setForeground(Color.RED.darker());
        JPanel hintRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        hintRow.setOpaque(false);
        hintRow.add(strengthLabel);
        hintRow.add(mismatchLabel);
        form.add(hintRow);
        form.add(Box.createVerticalStrut(10));

        buildAdvanced();
        form.add(advancedPanel);
        form.add(Box.createVerticalStrut(8));

        errorLabel.setForeground(Color.RED.darker());
        form.add(errorLabel);

        // 名称/位置变更时刷新最终路径
        DocumentListener refresh = new DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { updateFinalPath(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { updateFinalPath(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { updateFinalPath(); }
        };
        nameField.getDocument().addDocumentListener(refresh);
        locationField.getDocument().addDocumentListener(refresh);
        // 输入密码实时强度提示（zxcvbn，与 KeePassXC 同源）
        DocumentListener strengthRefresh = new DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { refreshStrength(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { refreshStrength(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { refreshStrength(); }
        };
        pwField.getDocument().addDocumentListener(strengthRefresh);
        confirmField.getDocument().addDocumentListener(strengthRefresh);
        return form;
    }

    private void buildAdvanced() {
        advancedPanel.setLayout(new BoxLayout(advancedPanel, BoxLayout.Y_AXIS));
        advancedPanel.setOpaque(false);
        javax.swing.border.TitledBorder advTitle = BorderFactory.createTitledBorder(
                "高级 (如果不理解这些设置项是什么，保持默认即可)");
        advTitle.setTitleJustification(javax.swing.border.TitledBorder.LEFT);
        advancedPanel.setBorder(BorderFactory.createCompoundBorder(
                advTitle,
                BorderFactory.createEmptyBorder(8, 10, 10, 10)));

        // 独立仓库：默认不勾选的单个勾选框；悬停显示原生提示气泡（不插入占用布局的控件）
        standaloneCheck.setOpaque(false);
        standaloneCheck.setToolTipText("<html><div style='width:320px'>" + STANDALONE_INFO + "</div></html>");
        advancedPanel.add(standaloneCheck);
        advancedPanel.add(Box.createVerticalStrut(8));
        advancedPanel.add(kdfPanel);
    }

    /** 独立仓库说明：鼠标悬停于勾选框时展示。 */
    private static final String STANDALONE_INFO =
            "勾选后，仓库根会自带启动脚本与运行依赖（standalone.json + lib/ + start.cmd），"
                    + "并可把应用级配置（不含密钥）复制为仓库级配置。"
                    + "之后该仓库可由自身的 start.cmd 独立启动，不再依赖本机安装的应用。"
                    + "不勾选则为普通仓库：仅建立数据块，需通过本应用打开。";

    private JPanel buildButtons() {
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        btns.setOpaque(false);
        JButton cancelBtn = new JButton("取消");
        cancelBtn.addActionListener(e -> dispose());
        JButton createBtn = new JButton("创建");
        createBtn.setPreferredSize(new Dimension(90, 30));
        createBtn.addActionListener(e -> confirm());
        btns.add(cancelBtn);
        btns.add(createBtn);
        getRootPane().setDefaultButton(createBtn);
        return btns;
    }

    private void chooseLocation() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("选择仓库位置");
        Path current = parsePath(locationField.getText());
        if (current != null && Files.isDirectory(current)) {
            chooser.setCurrentDirectory(current.toFile());
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            locationField.setText(chooser.getSelectedFile().toPath().toString());
        }
    }

    private void updateFinalPath() {
        Path target = computeTarget();
        if (target == null) {
            finalPathLabel.setText("仓库将被创建在：—");
        } else {
            finalPathLabel.setText("仓库将被创建在：" + target);
        }
    }

    private void confirm() {
        String name = effectiveName();
        Path location = parsePath(locationField.getText());
        if (location == null || !Files.isDirectory(location)) {
            errorLabel.setText("请选择有效的位置目录");
            return;
        }
        Path target = location.resolve(name);
        if (Files.exists(target)) {
            errorLabel.setText("目标目录已存在：" + target);
            return;
        }
        char[] pw = pwField.getPassword();
        if (pw.length == 0) {
            errorLabel.setText("请输入密码");
            return;
        }
        char[] confirm = confirmField.getPassword();
        if (!java.util.Arrays.equals(pw, confirm)) {
            errorLabel.setText("两次输入的密码不一致");
            return;
        }
        char[] pwCopy = pw.clone();
        java.util.Arrays.fill(pw, (char) 0);
        java.util.Arrays.fill(confirm, (char) 0);
        errorLabel.setText("");
        int[] kdf = kdfPanel.resolve();
        dispose();
        onConfirm.accept(new Request(target, standaloneCheck.isSelected(), kdf, pwCopy));
    }

    /** 有效仓库名：用户输入为空时回退到占位名「新密码仓库」。 */
    private String effectiveName() {
        String t = nameField.effectiveText().trim();
        return t.isEmpty() ? NAME_PLACEHOLDER : t;
    }

    private Path computeTarget() {
        String name = effectiveName();
        Path location = parsePath(locationField.getText());
        if (location == null) {
            return null;
        }
        return location.resolve(name);
    }

    private static Path parsePath(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Paths.get(s.trim());
        } catch (RuntimeException ignore) {
            return null;
        }
    }

    /** 默认位置：Windows 用「文档」，其它（Linux/macOS）用 home（~）。 */
    private static Path defaultLocation() {
        String os = System.getProperty("os.name", "").toLowerCase();
        Path home = Paths.get(System.getProperty("user.home"));
        if (os.contains("win")) {
            String docs = System.getProperty("user.home")
                    + File.separator + "Documents";
            Path d = Paths.get(docs);
            if (Files.isDirectory(d)) {
                return d;
            }
        }
        return home;
    }

    private static JPanel labeledRow(String label, JTextField field) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.add(new JLabel(label), BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        return row;
    }

    /** 实时刷新输入密码强度提示（标签），并用独立标签就地提示两次输入是否一致（不遮盖强度）。 */
    private void refreshStrength() {
        char[] pw = pwField.getPassword();
        char[] confirm = confirmField.getPassword();
        if (pw.length == 0) {
            strengthLabel.setText("");
            mismatchLabel.setText("");
            return;
        }
        PasswordStrength s = PasswordStrength.evaluate(new String(pw), null);
        strengthLabel.setText(strengthText(s));
        strengthLabel.setForeground(strengthColor(s.quality()));
        // 密码不一致单独提示，与强度同时展示
        if (confirm.length > 0 && !java.util.Arrays.equals(pw, confirm)) {
            mismatchLabel.setText("两次输入的密码不一致");
            mismatchLabel.setForeground(Color.RED.darker());
        } else {
            mismatchLabel.setText("");
        }
    }

    private static String strengthText(PasswordStrength s) {
        String level = switch (s.quality()) {
            case BAD -> "极差";
            case POOR -> "弱";
            case WEAK -> "中";
            case GOOD -> "良";
            case EXCELLENT -> "优";
        };
        StringBuilder sb = new StringBuilder("强度：").append(level)
                .append("（熵 ").append(String.format("%.1f", s.entropy())).append(" bits）");
        if (!s.warning().isEmpty()) {
            sb.append(" — ").append(s.warning());
        }
        return sb.toString();
    }

    private static Color strengthColor(PasswordStrength.Quality q) {
        return switch (q) {
            case BAD, POOR -> Color.RED.darker();
            case WEAK -> Color.ORANGE.darker();
            case GOOD -> Color.BLUE.darker();
            case EXCELLENT -> new Color(0x2E, 0x7D, 0x32);
        };
    }

    /**
     * 带占位符的文本框：为空（且未聚焦）时显示灰色占位符文本；聚焦或已输入时正常。
     * {@link #effectiveText()} 在占位符可见时返回空串，供调用方按默认名回退。
     */
    private static final class PlaceholderField extends JTextField {
        private final String placeholder;
        private final Color defaultFg;
        private boolean showing;

        PlaceholderField(int cols, String placeholder) {
            super(cols);
            this.placeholder = placeholder;
            Color def = javax.swing.UIManager.getColor("TextField.foreground");
            this.defaultFg = def != null ? def : Color.BLACK;
            showPlaceholder();
        }

        private void showPlaceholder() {
            if (showing) {
                return;
            }
            showing = true;
            setForeground(Color.GRAY);
            setText(placeholder);
        }

        private void hidePlaceholder() {
            if (!showing) {
                return;
            }
            showing = false;
            setText("");
            setForeground(defaultFg);
        }

        /** 有效文本：占位符可见时视为空。 */
        String effectiveText() {
            return showing ? "" : getText();
        }

        @Override
        protected void processFocusEvent(java.awt.event.FocusEvent e) {
            if (e.getID() == java.awt.event.FocusEvent.FOCUS_GAINED) {
                hidePlaceholder();
            } else if (e.getID() == java.awt.event.FocusEvent.FOCUS_LOST) {
                if (getText().trim().isEmpty()) {
                    showPlaceholder();
                }
            }
            super.processFocusEvent(e);
        }
    }
}
