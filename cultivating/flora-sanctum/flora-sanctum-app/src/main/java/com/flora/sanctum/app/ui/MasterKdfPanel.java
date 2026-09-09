package com.flora.sanctum.app.ui;

import com.flora.sanctum.app.BackgroundExecutor;
import com.flora.sanctum.core.crypto.Argon2KDF;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.Arrays;

/**
 * 设置页「主密码与 Argon2 参数」表单：新主密码/确认 + 内存/迭代/并行度三项，
 * 全部在同一页上由「保存」一次提交（换主密码与 KDF 参数由宿主在后台轮换执行）。
 *
 * <p>迭代次数行右侧带「测试」按钮：以当前输入的内存与并行度实测单次迭代耗时，
 * 估算使总耗时最接近 1 秒的严格整数迭代次数，并以「(n 次 ≈ x.xx 秒)」追加在
 * 迭代参数右侧括号内提示（不直接改动迭代输入值）。</p>
 */
final class MasterKdfPanel extends JPanel {

    /** 保存动作宿主：负责后台轮换与结果处理（成功后重建会话并离开设置页）；password 由宿主清零。 */
    interface Launcher {
        void launch(char[] password, int memoryKiB, int iterations, int parallelism);
    }

    private final Launcher launcher;
    private final BackgroundExecutor executor;
    private final PasswordField newPasswordField = new PasswordField(16);
    private final PasswordField confirmField = new PasswordField(16);
    private final JTextField memoryField = numberField(Argon2KDF.DEFAULT_MEMORY_KIB);
    private final JTextField iterationsField = numberField(Argon2KDF.DEFAULT_ITERATIONS);
    private final JTextField parallelismField = numberField(Argon2KDF.DEFAULT_PARALLELISM);
    private final JLabel iterationsHint = new JLabel("");
    private final JLabel errorLabel = new JLabel("", SwingConstants.LEFT);
    private final JButton testButton = new JButton("测试");

    MasterKdfPanel(int currentMemoryKiB, int currentIterations, int currentParallelism,
                   BackgroundExecutor executor, Launcher launcher) {
        this.launcher = launcher;
        this.executor = executor;
        setOpaque(false);
        setLayout(new BorderLayout(0, 6));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);

        newPasswordField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        confirmField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        content.add(fullRow("新主密码：", newPasswordField));
        content.add(Box.createVerticalStrut(6));
        content.add(fullRow("确认密码：", confirmField));
        content.add(Box.createVerticalStrut(10));
        content.add(buildArgon2Area());
        content.add(Box.createVerticalStrut(10));

        errorLabel.setForeground(Color.RED.darker());
        content.add(errorLabel);

        // 保存按钮：与其他设置项一致的一横条外观（整行、文字靠左）
        content.add(Box.createVerticalStrut(10));
        JButton saveButton = new JButton("保存");
        saveButton.setHorizontalAlignment(SwingConstants.LEFT);
        saveButton.setMargin(new java.awt.Insets(0, 8, 0, 0));
        saveButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        saveButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        saveButton.addActionListener(e -> save());
        content.add(saveButton);

        // BoxLayout 交叉轴按全部子件的 alignmentX 聚合出总对齐值：直接子件混用默认值
        // （Box 间距 0.5、JPanel/JLabel 0.0）会把左对齐的行整体右推。统一为 LEFT 消除偏移
        // （见 addition/exploration/explore20260906-01-swing-boxlayout-cross-axis-alignment.md）。
        for (Component c : content.getComponents()) {
            if (c instanceof JComponent jc) {
                jc.setAlignmentX(Component.LEFT_ALIGNMENT);
            }
        }

        add(content, BorderLayout.NORTH);

        memoryField.setText(String.valueOf(currentMemoryKiB));
        iterationsField.setText(String.valueOf(currentIterations));
        parallelismField.setText(String.valueOf(currentParallelism));

        // 面板自身放进设置右栏（Y Box）：占满整行宽、高度取自然值（避免被纵向拉伸）
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
    }

    /** Argon2 参数区：内存/迭代/并行度三行 + 迭代行右侧的测试按钮。 */
    private JPanel buildArgon2Area() {
        JPanel area = new JPanel();
        area.setLayout(new BoxLayout(area, BoxLayout.Y_AXIS));
        area.setOpaque(false);
        javax.swing.border.TitledBorder tb = BorderFactory.createTitledBorder("Argon2 参数");
        tb.setTitleJustification(javax.swing.border.TitledBorder.LEFT);
        area.setBorder(BorderFactory.createCompoundBorder(
                tb, BorderFactory.createEmptyBorder(6, 10, 8, 10)));

        area.add(paramRow("内存 (KiB)：", memoryField));
        area.add(Box.createVerticalStrut(6));
        area.add(iterationsRow());
        area.add(Box.createVerticalStrut(6));
        area.add(paramRow("并行度：", parallelismField));
        area.add(Box.createVerticalStrut(8));

        // 同上：区域内直接子件统一 LEFT，使标题框与各行的左边缘对齐且行占满
        for (Component c : area.getComponents()) {
            if (c instanceof JComponent jc) {
                jc.setAlignmentX(Component.LEFT_ALIGNMENT);
            }
        }
        area.setAlignmentX(Component.LEFT_ALIGNMENT);
        area.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        iterationsHint.setForeground(Color.GRAY);
        iterationsHint.setText("(点击“测试”估算)");
        testButton.setToolTipText("按当前内存与并行度实测单次迭代耗时，估算总耗时最接近 1 秒的迭代次数");
        testButton.addActionListener(e -> runBenchmark());
        return area;
    }

    /** 参数区整行：标签 + 控件（FlowLayout 左对齐，行占满宽）。 */
    private static JPanel paramRow(String label, Component c) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        row.add(new JLabel(label));
        row.add(c);
        return row;
    }

    /** 迭代次数行：输入框 + 右侧括号提示 + 测试按钮。 */
    private JPanel iterationsRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        row.add(new JLabel("迭代次数："));
        row.add(iterationsField);
        row.add(iterationsHint);
        row.add(testButton);
        return row;
    }

    /** 密码整行：标签在左、输入占满剩余宽度（与解锁/新建页一致）。 */
    private static JPanel fullRow(String label, Component c) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        row.add(new JLabel(label), BorderLayout.WEST);
        row.add(c, BorderLayout.CENTER);
        return row;
    }

    private static JTextField numberField(int def) {
        JTextField f = new JTextField(Integer.toString(def), 8);
        f.setMaximumSize(new Dimension(120, 26));
        return f;
    }

    /** 保存：本地校验通过后把密码与参数交给宿主执行轮换（成功后宿主自行切页）。 */
    private void save() {
        char[] pw = newPasswordField.getPassword();
        char[] confirm = confirmField.getPassword();
        if (pw.length == 0) {
            setError("请输入新主密码");
            clear(pw);
            clear(confirm);
            return;
        }
        if (!Arrays.equals(pw, confirm)) {
            setError("两次输入的新密码不一致");
            clear(pw);
            clear(confirm);
            return;
        }
        clear(confirm);
        Integer m = parse(memoryField.getText());
        Integer i = parse(iterationsField.getText());
        Integer p = parse(parallelismField.getText());
        if (m == null || i == null || p == null) {
            setError("内存/迭代/并行度须为正整数");
            clear(pw);
            return;
        }
        try {
            Argon2KDF.validate(m, i, p);
        } catch (IllegalArgumentException e) {
            setError("参数不满足 Argon2 约束：内存 (KiB) 不得小于 8×并行度");
            clear(pw);
            return;
        }
        clearError();
        launcher.launch(pw, m, i, p);
    }

    /**
     * 测试：两步折算实测单次迭代耗时并给出「总耗时最接近 1 秒」的括号建议。
     * 算法与后台调度见 {@link Argon2IterationProbe}（BackgroundExecutor 串行执行、回调在 EDT）。
     */
    private void runBenchmark() {
        Integer m = parse(memoryField.getText());
        Integer p = parse(parallelismField.getText());
        if (m == null || p == null) {
            setError("请输入合法的内存与并行度后再测试");
            return;
        }
        try {
            Argon2KDF.validate(m, 1, p);
        } catch (IllegalArgumentException e) {
            setError("参数不满足 Argon2 约束：内存 (KiB) 不得小于 8×并行度");
            return;
        }
        clearError();
        testButton.setEnabled(false);
        iterationsHint.setText("正在测试…");
        Argon2IterationProbe.run(executor, m, p, new Argon2IterationProbe.Listener() {
            @Override
            public void onDone(double perIterationSeconds) {
                testButton.setEnabled(true);
                iterationsHint.setText(Argon2IterationProbe.hintText(perIterationSeconds));
            }

            @Override
            public void onError(String message) {
                testButton.setEnabled(true);
                iterationsHint.setText("");
                setError(message);
            }
        });
    }

    private static Integer parse(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.isEmpty()) {
            return null;
        }
        try {
            int v = Integer.parseInt(t);
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void clear(char[] cs) {
        if (cs != null) {
            Arrays.fill(cs, (char) 0);
        }
    }

    private void setError(String msg) {
        errorLabel.setText(msg);
    }

    private void clearError() {
        errorLabel.setText("");
    }
}
