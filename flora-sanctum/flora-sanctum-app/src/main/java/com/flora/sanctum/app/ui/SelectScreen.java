package com.flora.sanctum.app.ui;

import com.flora.sanctum.app.bootstrap.RepoCreator;
import com.flora.sanctum.app.bootstrap.RepoImporter;
import com.flora.sanctum.app.bootstrap.VaultForm;
import com.flora.root.codec.json.model.JsonObject;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.nio.file.Path;

/**
 * 应用形态首次进入的选择界面（见设计"形态与启动"）。
 * <p>
 * 三个入口：新建 / 导入 / 打开。新建分普通/独立仓库；导入为 git clone 并按结构分类；
 * 打开为选择已存在仓库。选定后把仓库交给 {@link SanctumGui} 打开对应 data 根。
 */
public final class SelectScreen {

    private final JFrame frame;
    private final java.util.function.Consumer<Path> opener;

    /**
     * @param opener 打开回调：传入选定仓库的 vault 根（data），由上层启动 SanctumGui
     */
    public SelectScreen(java.util.function.Consumer<Path> opener) {
        this.opener = opener;
        this.frame = new JFrame("flora-sanctum");
    }

    public void show() {
        UiTheme.apply();
        JPanel root = new UiTheme.PaperPanel(new BorderLayout(0, 16));
        root.setBorder(new EmptyBorder(24, 28, 24, 28));

        JLabel title = new JLabel("flora-sanctum");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        title.setHorizontalAlignment(JLabel.CENTER);
        root.add(title, BorderLayout.NORTH);

        JPanel buttons = new JPanel(new GridLayout(1, 3, 12, 0));
        buttons.setOpaque(false);
        buttons.add(makeButton("新建", "建立一个新的密码仓库（普通或独立）", this::newVault));
        buttons.add(makeButton("导入", "从远程 git 仓库克隆导入", this::importVault));
        buttons.add(makeButton("打开", "打开已存在的仓库", this::openVault));
        root.add(buttons, BorderLayout.CENTER);

        frame.setContentPane(root);
        frame.setSize(560, 180);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    private JButton makeButton(String label, String tooltip, Runnable action) {
        JButton b = new JButton(label);
        b.setToolTipText(tooltip);
        b.addActionListener(e -> action.run());
        return b;
    }

    private void newVault() {
        Object[] choices = {"普通仓库", "独立仓库"};
        int choice = JOptionPane.showOptionDialog(frame, "选择要建立的仓库类型：", "新建仓库",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, choices, choices[0]);
        if (choice < 0) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("选择目标目录");
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path dir = chooser.getSelectedFile().toPath();
        try {
            if (choice == 0) {
                Path root = RepoCreator.createNormal(dir);
                opener.accept(root);
            } else {
                // 独立仓库：复制应用自身 lib + 应用级配置为仓库级
                JsonObject appConfig = loadAppConfig();
                RepoCreator.createStandalone(dir, libSource(), appConfig);
                JOptionPane.showMessageDialog(frame,
                        "独立仓库已创建。请用仓库内的启动脚本（start.cmd / start.sh）启动。");
                // 不打开（独立仓库由自身脚本启动）
                frame.dispose();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "创建失败：" + ex.getMessage(), "错误",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void importVault() {
        JPanel form = new JPanel(new GridLayout(2, 2, 8, 8));
        form.setOpaque(false);
        JTextField urlField = new JTextField(28);
        JTextField dirField = new JTextField(28);
        form.add(new JLabel("远程地址："));
        form.add(urlField);
        form.add(new JLabel("本地目录："));
        form.add(dirField);
        int r = JOptionPane.showConfirmDialog(frame, form, "导入仓库", JOptionPane.OK_CANCEL_OPTION);
        if (r != JOptionPane.OK_OPTION) {
            return;
        }
        String url = urlField.getText().trim();
        String local = dirField.getText().trim();
        if (url.isEmpty() || local.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "远程地址与本地目录不能为空", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        try {
            RepoImporter.Result result = RepoImporter.importRemote(url, Path.of(local));
            if (result.vaultRoot != null) {
                opener.accept(result.vaultRoot);
            }
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(frame, e.getMessage(), "导入失败", JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "导入失败：" + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openVault() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            Path dir = chooser.getSelectedFile().toPath();
            if (VaultForm.detect(dir) == VaultForm.Type.NOT_A_VAULT) {
                JOptionPane.showMessageDialog(frame, "该目录不是 flora-sanctum 仓库", "打开失败",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            Path vaultRoot = VaultForm.vaultRoot(dir);
            if (vaultRoot != null) {
                opener.accept(vaultRoot);
            }
        }
    }

    private static Path libSource() {
        // 应用分发形态下 lib 目录：优先系统属性，其次 classpath 所在 jar 目录（简化：不解析）。
        String prop = System.getProperty("flora.lib");
        return prop != null ? Path.of(prop) : null;
    }

    private static JsonObject loadAppConfig() {
        try {
            return com.flora.root.codec.JsonUtil.parseObject(
                    java.nio.file.Files.readString(
                            Path.of(System.getProperty("user.home"), ".flora-sanctum", "config.json")));
        } catch (Exception e) {
            return new JsonObject();
        }
    }
}
