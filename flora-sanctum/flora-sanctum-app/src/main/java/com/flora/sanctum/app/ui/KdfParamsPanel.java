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

/**
 * 新建库时的 Argon2id 强度设置（作为「高级」框内的嵌套「Argon2id」框）。
 * <p>提供内存(KiB)/迭代次数/并行度三项，预填默认高安全档；任一输入非法或为空时
 * {@link #resolve()} 返回 {@code null}，调用方据此回退到默认档。</p>
 * <p>迭代次数行右侧带「测试」按钮：按当前内存与并行度两步实测单次迭代耗时，
 * 估算总耗时最接近 1 秒的严格整数迭代数，并以「(n 次 ≈ x.xx 秒)」括号提示
 * （不直接改动迭代输入值），供建库前参考。</p>
 */
final class KdfParamsPanel extends JPanel {

    private final JTextField memoryField;
    private final JTextField iterationsField;
    private final JTextField parallelismField;
    private final JLabel iterationsHint = new JLabel("");
    private final JLabel errorLabel = new JLabel("", SwingConstants.LEFT);
    private final JButton testButton = new JButton("测试");

    KdfParamsPanel(BackgroundExecutor executor) {
        super(new BorderLayout(0, 4));
        setOpaque(false);
        javax.swing.border.TitledBorder tb = BorderFactory.createTitledBorder("Argon2id");
        tb.setTitleJustification(javax.swing.border.TitledBorder.LEFT);
        setBorder(tb);

        JPanel grid = new JPanel();
        grid.setLayout(new BoxLayout(grid, BoxLayout.Y_AXIS));
        grid.setOpaque(false);

        memoryField = numberField(Argon2KDF.DEFAULT_MEMORY_KIB);
        iterationsField = numberField(Argon2KDF.DEFAULT_ITERATIONS);
        parallelismField = numberField(Argon2KDF.DEFAULT_PARALLELISM);

        grid.add(paramRow("内存 (KiB)：", memoryField));
        grid.add(Box.createVerticalStrut(6));
        grid.add(iterationsRow());
        grid.add(Box.createVerticalStrut(6));
        grid.add(paramRow("并行度：", parallelismField));
        grid.add(Box.createVerticalStrut(8));

        errorLabel.setForeground(Color.RED.darker());
        grid.add(errorLabel);
        // BoxLayout 交叉轴按全部子件的 alignmentX 聚合：直接子件混用默认值会把左对齐行右推，
        // 统一 LEFT 使各参数行左边缘一致（见 explore20260906-01-swing-boxlayout-cross-axis-alignment.md）。
        for (Component c : grid.getComponents()) {
            if (c instanceof JComponent jc) {
                jc.setAlignmentX(Component.LEFT_ALIGNMENT);
            }
        }
        add(grid, BorderLayout.NORTH);

        JLabel hint = new JLabel("默认 " + Argon2KDF.DEFAULT_MEMORY_KIB + " KiB / "
                + Argon2KDF.DEFAULT_ITERATIONS + " 轮 / " + Argon2KDF.DEFAULT_PARALLELISM
                + " 线程；数值越大越安全也越慢");
        hint.setForeground(Color.GRAY);
        add(hint, BorderLayout.SOUTH);

        iterationsHint.setForeground(Color.GRAY);
        iterationsHint.setText("(点击“测试”估算)");
        testButton.setToolTipText("按当前内存与并行度实测单次迭代耗时，估算总耗时最接近 1 秒的迭代次数");
        testButton.addActionListener(e -> runBenchmark(executor));
    }

    private static JTextField numberField(int def) {
        JTextField f = new JTextField(Integer.toString(def), 8);
        f.setMaximumSize(new Dimension(120, 26));
        return f;
    }

    /** 参数区整行：标签 + 控件（FlowLayout 左对齐，行占满宽）。 */
    private static JPanel paramRow(String label, Component c) {
        JPanel r = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        r.setOpaque(false);
        r.setAlignmentX(Component.LEFT_ALIGNMENT);
        r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        r.add(new JLabel(label));
        r.add(c);
        return r;
    }

    /** 迭代次数行：输入框 + 右侧括号提示 + 测试按钮。 */
    private JPanel iterationsRow() {
        JPanel r = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        r.setOpaque(false);
        r.setAlignmentX(Component.LEFT_ALIGNMENT);
        r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        r.add(new JLabel("迭代次数："));
        r.add(iterationsField);
        r.add(iterationsHint);
        r.add(testButton);
        return r;
    }

    /**
     * 测试：两步折算实测单次迭代耗时并给出「总耗时最接近 1 秒」的括号建议。
     * 算法与后台调度见 {@link Argon2IterationProbe}（BackgroundExecutor 串行执行、回调在 EDT）。
     */
    private void runBenchmark(BackgroundExecutor executor) {
        Integer m = parse(memoryField.getText());
        Integer p = parse(parallelismField.getText());
        if (m == null || p == null) {
            errorLabel.setText("请输入合法的内存与并行度后再测试");
            return;
        }
        try {
            Argon2KDF.validate(m, 1, p);
        } catch (IllegalArgumentException e) {
            errorLabel.setText("参数不满足 Argon2 约束：内存 (KiB) 不得小于 8×并行度");
            return;
        }
        errorLabel.setText("");
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
                errorLabel.setText(message);
            }
        });
    }

    /**
     * 解析三项参数为 {@code {memoryKiB, iterations, parallelism}}。
     * 任一为空或非法（非正整数/不满足 Argon2 约束）时返回 {@code null}，表示回退默认档；
     * 约束由 core 的 {@link Argon2KDF#validate} 定义。
     */
    int[] resolve() {
        Integer m = parse(memoryField.getText());
        Integer i = parse(iterationsField.getText());
        Integer p = parse(parallelismField.getText());
        if (m == null || i == null || p == null) {
            return null;
        }
        try {
            Argon2KDF.validate(m, i, p);
        } catch (IllegalArgumentException e) {
            return null;
        }
        return new int[]{m, i, p};
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
}
