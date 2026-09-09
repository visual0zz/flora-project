package com.flora.sanctum.app.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import com.flora.sanctum.app.sync.SyncService.SyncStepListener;

/**
 * 同步进度窗（非模态）：上方列出各步骤及状态标记，下方显示 git 原始日志。
 * 实现 {@link SyncStepListener}，由后台同步线程回调；所有更新经 EDT 串行化。
 * 「关闭」按钮默认禁用，同步结束或出错时启用。
 */
public final class SyncProgressDialog extends JDialog implements SyncStepListener {

    private enum Status {
        PENDING("·", new Color(150, 150, 150)),
        RUNNING("→", new Color(70, 130, 220)),
        DONE("✓", new Color(60, 170, 90)),
        ERROR("✗", new Color(210, 70, 70));

        final String mark;
        final Color color;

        Status(String mark, Color color) {
            this.mark = mark;
            this.color = color;
        }
    }

    private static final class StepRow {
        final int index;
        final String title;
        Status status = Status.PENDING;
        String detail = "";

        StepRow(int index, String title) {
            this.index = index;
            this.title = title;
        }

        @Override
        public String toString() {
            return status.mark + "  " + title + (detail.isEmpty() ? "" : "  —  " + detail);
        }

        private String mark() {
            return status.mark;
        }
    }

    private final List<StepRow> rows = new ArrayList<>();
    private final DefaultListModel<StepRow> model = new DefaultListModel<>();
    private final JList<StepRow> stepList = new JList<>(model);
    private final JTextArea logArea = new JTextArea(8, 46);
    private final JButton startBtn = new JButton("开始同步");
    private final JButton closeBtn = new JButton("关闭");
    private final JLabel summary = new JLabel("准备就绪 — 点击「开始同步」执行云同步");

    /** 由外部注入：点击「开始同步」按钮时执行（实际同步逻辑在后台线程运行）。 */
    private Runnable startAction;

    public SyncProgressDialog(java.awt.Window parent) {
        super(parent, "云同步进度", ModalityType.MODELESS);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        initUi();
        pack();
        setLocationRelativeTo(parent);
    }

    /** 注册「开始同步」按钮的点击动作；点击前不会自动开始同步。 */
    public void setOnStart(Runnable action) {
        this.startAction = action;
    }

    private void initUi() {
        stepList.setCellRenderer(new StepCellRenderer());
        stepList.setVisibleRowCount(10);
        JScrollPane stepScroll = new JScrollPane(stepList);
        stepScroll.setPreferredSize(new Dimension(420, 220));

        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setPreferredSize(new Dimension(420, 130));

        summary.setBorder(new EmptyBorder(4, 2, 4, 2));

        closeBtn.setEnabled(false);
        closeBtn.addActionListener(e -> dispose());
        startBtn.addActionListener(e -> {
            if (startAction != null) {
                startBtn.setEnabled(false);
                startAction.run();
            }
        });
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnRow.add(startBtn);
        btnRow.add(closeBtn);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(new EmptyBorder(10, 10, 10, 10));
        content.add(summary, BorderLayout.NORTH);
        content.add(stepScroll, BorderLayout.CENTER);
        content.add(logScroll, BorderLayout.SOUTH);
        JPanel wrap = new JPanel(new BorderLayout(0, 8));
        wrap.add(content, BorderLayout.CENTER);
        wrap.add(btnRow, BorderLayout.SOUTH);
        setContentPane(wrap);
    }

    // ============ SyncStepListener 实现（EDT 串行化） ============

    @Override
    public void beginStep(int index, String title) {
        SwingUtilities.invokeLater(() -> {
            StepRow row = new StepRow(index, title);
            rows.add(row);
            model.addElement(row);
        });
    }

    @Override
    public void markRunning(int index) {
        SwingUtilities.invokeLater(() -> {
            StepRow row = rowAt(index);
            if (row != null) {
                row.status = Status.RUNNING;
                row.detail = "";
                model.set(model.indexOf(row), row);
            }
        });
    }

    @Override
    public void markDone(int index, String detail) {
        SwingUtilities.invokeLater(() -> {
            StepRow row = rowAt(index);
            if (row != null) {
                row.status = Status.DONE;
                row.detail = detail == null ? "" : detail;
                model.set(model.indexOf(row), row);
                stepList.ensureIndexIsVisible(model.indexOf(row));
            }
        });
    }

    @Override
    public void markError(int index, String detail) {
        SwingUtilities.invokeLater(() -> {
            StepRow row = rowAt(index);
            if (row != null) {
                row.status = Status.ERROR;
                row.detail = detail == null ? "" : detail;
                model.set(model.indexOf(row), row);
                stepList.ensureIndexIsVisible(model.indexOf(row));
            }
        });
    }

    @Override
    public void log(String message) {
        SwingUtilities.invokeLater(() -> {
            if (message != null && !message.isBlank()) {
                logArea.append(message.endsWith("\n") ? message : message + "\n");
                logArea.setCaretPosition(logArea.getDocument().getLength());
            }
        });
    }

    @Override
    public void done(boolean ok, String message) {
        SwingUtilities.invokeLater(() -> {
            summary.setText((ok ? "同步完成" : "同步失败") + "：" + (message == null ? "" : message));
            summary.setForeground(ok ? new Color(60, 170, 90) : new Color(210, 70, 70));
            closeBtn.setEnabled(true);
        });
    }

    private StepRow rowAt(int index) {
        for (StepRow r : rows) {
            if (r.index == index) {
                return r;
            }
        }
        return null;
    }

    private static final class StepCellRenderer implements ListCellRenderer<StepRow> {
        private final JLabel label = new JLabel();
        private final EmptyBorder border = new EmptyBorder(2, 4, 2, 4);

        @Override
        public Component getListCellRendererComponent(JList<? extends StepRow> list,
                StepRow value, int index, boolean selected, boolean focused) {
            label.setText(value == null ? "" : value.toString());
            if (value != null) {
                label.setForeground(value.status.color);
            }
            label.setBorder(border);
            label.setOpaque(true);
            label.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            return label;
        }
    }
}
