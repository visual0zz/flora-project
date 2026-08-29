package com.flora.sanctum.app.ui;

import com.flora.sanctum.crypto.Argon2KDF;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/**
 * 新建库时的 Argon2id 强度设置（作为「高级」框内的嵌套「Argon2id」框）。
 * <p>提供内存(KiB)/迭代次数/并行度三项；预填默认高安全档。任一输入非法或为空时
 * {@link #resolve()} 返回 {@code null}，调用方据此回退到默认档。</p>
 */
final class KdfParamsPanel extends JPanel {

    private final JTextField memoryField;
    private final JTextField iterationsField;
    private final JTextField parallelismField;
    private final JLabel hint;

    KdfParamsPanel() {
        super(new BorderLayout(0, 4));
        setOpaque(false);
        javax.swing.border.TitledBorder tb = BorderFactory.createTitledBorder("Argon2id");
        tb.setTitleJustification(javax.swing.border.TitledBorder.LEFT);
        setBorder(tb);

        JPanel grid = new JPanel();
        grid.setLayout(new BoxLayout(grid, BoxLayout.Y_AXIS));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);

        memoryField = numberField(Argon2KDF.DEFAULT_MEMORY_KIB);
        iterationsField = numberField(Argon2KDF.DEFAULT_ITERATIONS);
        parallelismField = numberField(Argon2KDF.DEFAULT_PARALLELISM);

        grid.add(row("内存 (KiB)：", memoryField));
        grid.add(Box.createVerticalStrut(6));
        grid.add(row("迭代次数：", iterationsField));
        grid.add(Box.createVerticalStrut(6));
        grid.add(row("并行度：", parallelismField));

        add(grid, BorderLayout.NORTH);

        hint = new JLabel("默认 " + Argon2KDF.DEFAULT_MEMORY_KIB + " KiB / "
                + Argon2KDF.DEFAULT_ITERATIONS + " 轮 / " + Argon2KDF.DEFAULT_PARALLELISM
                + " 线程；数值越大越安全也越慢");
        hint.setForeground(Color.GRAY);
        add(hint, BorderLayout.SOUTH);
    }

    private static JTextField numberField(int def) {
        JTextField f = new JTextField(Integer.toString(def), 10);
        f.setMaximumSize(new Dimension(120, 24));
        return f;
    }

    /** 一行：标签 + 输入框（左对齐，不加 glue，避免内容被居中分配到右侧）。 */
    private static JPanel row(String label, JTextField f) {
        JPanel r = new JPanel();
        r.setLayout(new BoxLayout(r, BoxLayout.X_AXIS));
        r.setOpaque(false);
        r.setAlignmentX(Component.LEFT_ALIGNMENT);
        r.add(new JLabel(label));
        r.add(Box.createHorizontalStrut(8));
        r.add(f);
        return r;
    }

    /**
     * 解析三项参数为 {@code {memoryKiB, iterations, parallelism}}。
     * 任一为空或非法（非正整数）时返回 {@code null}，表示回退默认档。
     */
    int[] resolve() {
        Integer m = parse(memoryField.getText());
        Integer i = parse(iterationsField.getText());
        Integer p = parse(parallelismField.getText());
        if (m == null || i == null || p == null) {
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
