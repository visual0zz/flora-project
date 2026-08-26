package com.flora.sanctum.app.ui;

import com.flora.sanctum.crypto.Argon2KDF;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/**
 * 新建库时的 Argon2id 强度设置（折叠于「高级」下）。
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
        setBorder(BorderFactory.createTitledBorder("高级（Argon2id 强度，留空用默认）"));

        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 4, 2, 4);
        c.anchor = GridBagConstraints.WEST;

        memoryField = numberField(Argon2KDF.DEFAULT_MEMORY_KIB);
        iterationsField = numberField(Argon2KDF.DEFAULT_ITERATIONS);
        parallelismField = numberField(Argon2KDF.DEFAULT_PARALLELISM);

        c.gridx = 0; c.gridy = 0; grid.add(new JLabel("内存 (KiB)："), c);
        c.gridx = 1; grid.add(memoryField, c);
        c.gridx = 0; c.gridy = 1; grid.add(new JLabel("迭代次数："), c);
        c.gridx = 1; grid.add(iterationsField, c);
        c.gridx = 0; c.gridy = 2; grid.add(new JLabel("并行度："), c);
        c.gridx = 1; grid.add(parallelismField, c);

        add(grid, BorderLayout.CENTER);

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
