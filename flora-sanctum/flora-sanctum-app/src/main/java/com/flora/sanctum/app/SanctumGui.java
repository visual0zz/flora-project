package com.flora.sanctum.app;

import com.flora.root.codec.json.model.JsonObject;
import com.flora.sanctum.config.UserConfig;
import com.flora.sanctum.model.Sanctum;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * flora-sanctum Swing GUI（无参数启动）。
 * <p>
 * 用 Swing（JDK 自带，纯 Java）实现：解锁屏、主界面、系统托盘、自动锁定、
 * 剪贴板定时清空、Material 明/暗主题、同步按钮、TOTP 倒计时、设置页。
 * 相比 JavaFX：纯 JDK 可 jlink 打包精简 JRE、免装 Java、一次打包。
 * UI 只调用 core 公开 API（见设计 07），不解密、不碰 Git。
 */
public final class SanctumGui {

    private final java.util.concurrent.atomic.AtomicReference<Sanctum> current =
            new java.util.concurrent.atomic.AtomicReference<>();
    private final UserConfig config = new UserConfig();
    private com.flora.sanctum.server.SanctumHttpServer httpServer;
    private Sanctum sanctum;
    private JFrame frame;
    private JLabel statusLabel;
    private String copiedPlaintext;
    private java.util.Timer autoLockTimer;
    private java.util.Timer clipboardTimer;

    /** 入口（与 CLI 分流：无参走 GUI）。 */
    public static void launch(String[] args) {
        new SanctumGui().run(args);
    }

    private void run(String[] args) {
        try {
            httpServer = new com.flora.sanctum.server.SanctumHttpServer(current::get, 0);
            httpServer.start();
        } catch (IOException e) {
            throw new IllegalStateException("cannot start HTTP server", e);
        }
        SwingUtilities.invokeLater(() -> {
            frame = new JFrame("flora-sanctum");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            installTray();
            applyTheme();
            frame.setContentPane(buildUnlockPanel());
            frame.setSize(400, 300);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    private void shutdown() {
        if (httpServer != null) {
            httpServer.stop();
        }
        stopTimers();
        System.exit(0);
    }

    private void stopTimers() {
        if (autoLockTimer != null) {
            autoLockTimer.cancel();
            autoLockTimer = null;
        }
        if (clipboardTimer != null) {
            clipboardTimer.cancel();
            clipboardTimer = null;
        }
    }

    // ---- 主题（明暗/跟随系统）----

    private void applyTheme() {
        String theme = config.theme();
        boolean dark = "dark".equals(theme) || ("system".equals(theme) && isSystemDark());
        // Swing 明暗由系统 L&F 决定；完整 Material 可后续引 FlatLaf
        // 此处保留主题配置接口，实际用系统默认外观
    }

    private boolean isSystemDark() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) {
            String apple = System.getProperty("apple.awt.application.appearance");
            return apple != null && apple.contains("dark");
        }
        return false;
    }

    // ---- 系统托盘 ----

    private void installTray() {
        if (!java.awt.SystemTray.isSupported()) {
            return;
        }
        java.awt.TrayIcon icon = new java.awt.TrayIcon(
                java.awt.Toolkit.getDefaultToolkit().createImage(new byte[0]), "flora-sanctum");
        java.awt.PopupMenu menu = new java.awt.PopupMenu();
        java.awt.MenuItem lockItem = new java.awt.MenuItem("锁定");
        lockItem.addActionListener(e -> lock());
        java.awt.MenuItem copyItem = new java.awt.MenuItem("复制密码");
        copyItem.addActionListener(e -> {
            if (copiedPlaintext != null) {
                setClipboard(copiedPlaintext);
            }
        });
        java.awt.MenuItem exitItem = new java.awt.MenuItem("退出");
        exitItem.addActionListener(e -> shutdown());
        menu.add(lockItem);
        menu.add(copyItem);
        menu.addSeparator();
        menu.add(exitItem);
        icon.setPopupMenu(menu);
        try {
            java.awt.SystemTray.getSystemTray().add(icon);
        } catch (java.awt.AWTException ignore) {
        }
    }

    // ---- 解锁屏 ----

    private JPanel buildUnlockPanel() {
        JPanel panel = new JPanel(new GridLayout(6, 1, 8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel dirLabel = new JLabel("未选择库目录");
        JButton dirBtn = new JButton("选择库目录…");
        dirBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                dirLabel.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });

        JTextField pathField = new JTextField();
        pathField.setToolTipText("或直接输入库路径");
        JPasswordField pwField = new JPasswordField();
        pwField.setToolTipText("主密码");
        JButton unlockBtn = new JButton("解锁");
        JLabel error = new JLabel();

        unlockBtn.addActionListener(e -> doUnlock(pathField, dirLabel, pwField, error));
        pwField.addActionListener(e -> doUnlock(pathField, dirLabel, pwField, error));

        panel.add(dirLabel);
        panel.add(dirBtn);
        panel.add(pathField);
        panel.add(pwField);
        panel.add(unlockBtn);
        panel.add(error);
        return panel;
    }

    private void doUnlock(JTextField pathField, JLabel dirLabel, JPasswordField pwField, JLabel error) {
        String path = pathField.getText().trim();
        if (path.isEmpty()) {
            path = dirLabel.getText();
        }
        if (path.isEmpty() || "未选择库目录".equals(path)) {
            error.setText("请选择库目录");
            return;
        }
        char[] pw = pwField.getPassword();
        try {
            Path root = Path.of(path);
            boolean exists = Files.isDirectory(root);
            if (exists) {
                sanctum = Sanctum.open(root);
            } else {
                sanctum = Sanctum.createAndUnlock(root, pw);
            }
            if (sanctum.isUnlocked()) {
                sanctum.unlock(pw);
            }
            current.set(sanctum);
            frame.setContentPane(buildMainPanel());
            frame.setSize(900, 600);
            frame.revalidate();
            startAutoLockTimer();
        } catch (Exception ex) {
            // 统一提示"解锁失败"，不区分密码错/数据损坏（见设计 03）
            error.setText("解锁失败");
        } finally {
            java.util.Arrays.fill(pw, (char) 0);
        }
    }

    // ---- 自动锁定 ----

    private void startAutoLockTimer() {
        if (autoLockTimer != null) {
            autoLockTimer.cancel();
        }
        autoLockTimer = new java.util.Timer(true);
        autoLockTimer.schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(SanctumGui.this::lock);
            }
        }, config.lockTimeoutSeconds() * 1000L);
    }

    private void resetAutoLock() {
        if (sanctum != null && sanctum.isUnlocked()) {
            startAutoLockTimer();
        }
    }

    private void lock() {
        if (sanctum != null) {
            sanctum.close();
        }
        current.set(null);
        stopTimers();
        frame.setContentPane(buildUnlockPanel());
        frame.setSize(400, 300);
        frame.revalidate();
    }

    // ---- 主界面 ----

    private JPanel buildMainPanel() {
        JPanel root = new JPanel(new BorderLayout());

        JButton syncBtn = new JButton("同步");
        JButton settingsBtn = new JButton("设置");
        JButton lockBtn = new JButton("锁定");
        statusLabel = new JLabel();
        syncBtn.setVisible(isFullyManaged());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        top.add(syncBtn);
        top.add(settingsBtn);
        top.add(lockBtn);
        top.add(statusLabel);

        JTree tree = new JTree(new DefaultMutableTreeNode("全部"));
        tree.setRootVisible(true);

        JList<String> entryList = new JList<>();
        javax.swing.DefaultListModel<String> model = new javax.swing.DefaultListModel<>();
        entryList.setModel(model);
        JScrollPane entryScroll = new JScrollPane(entryList);

        JTextField editField = new JTextField();
        JButton copyBtn = new JButton("复制");
        JLabel totpLabel = new JLabel();
        JPanel editPanel = new JPanel(new GridLayout(5, 1, 6, 6));
        editPanel.setBorder(BorderFactory.createTitledBorder("编辑"));
        editPanel.add(new JLabel("字段值"));
        editPanel.add(editField);
        editPanel.add(copyBtn);
        editPanel.add(new JLabel("TOTP"));
        editPanel.add(totpLabel);

        copyBtn.addActionListener(e -> copySelected(editField));
        entryList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                resetAutoLock();
                updateTotpDisplay(entryList, totpLabel);
            }
        });
        syncBtn.addActionListener(e -> doSync());
        lockBtn.addActionListener(e -> lock());
        settingsBtn.addActionListener(e -> {
            frame.setContentPane(buildSettingsPanel());
            frame.revalidate();
        });

        refreshEntryList(model);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(tree),
                new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, entryScroll, editPanel));
        split.setDividerLocation(200);
        root.add(top, BorderLayout.NORTH);
        root.add(split, BorderLayout.CENTER);
        return root;
    }

    private boolean isFullyManaged() {
        return sanctum != null
                && new com.flora.sanctum.sync.SyncService(sanctum.root()).isFullyManaged();
    }

    private void refreshEntryList(javax.swing.DefaultListModel<String> model) {
        model.clear();
        for (UUID u : sanctum.listObjectUuids()) {
            JsonObject n = sanctum.getEntry(u);
            if (n != null) {
                model.addElement(n.getString("type") + ": " + n.getString("name") + "  [" + u + "]");
            }
        }
    }

    private void copySelected(JTextField editField) {
        resetAutoLock();
        String value = editField.getText();
        if (value.isEmpty()) {
            return;
        }
        setClipboard(value);
        copiedPlaintext = value;
        startClipboardTimer();
    }

    private void setClipboard(String value) {
        Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
        clip.setContents(new StringSelection(value), null);
    }

    private void startClipboardTimer() {
        if (clipboardTimer != null) {
            clipboardTimer.cancel();
        }
        clipboardTimer = new java.util.Timer(true);
        clipboardTimer.schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(() -> setClipboard(""));
                copiedPlaintext = null;
            }
        }, config.clipboardClearSeconds() * 1000L);
    }

    private void doSync() {
        resetAutoLock();
        if (sanctum == null) {
            return;
        }
        try {
            com.flora.sanctum.sync.SyncService sync = new com.flora.sanctum.sync.SyncService(sanctum.root());
            if (!sync.isFullyManaged()) {
                statusLabel.setText("非完全托管，跳过同步");
                return;
            }
            sanctum.close();
            sync.sync();
            sanctum = Sanctum.open(sanctum.root());
            current.set(sanctum);
            statusLabel.setText("已同步");
        } catch (Exception e) {
            statusLabel.setText("同步失败");
        }
    }

    private void updateTotpDisplay(JList<String> entryList, JLabel totpLabel) {
        String sel = entryList.getSelectedValue();
        if (sel == null || !sel.startsWith("field:")) {
            totpLabel.setText("");
            return;
        }
        try {
            String uuidStr = sel.substring(sel.lastIndexOf('[') + 1, sel.lastIndexOf(']'));
            UUID fieldUuid = UUID.fromString(uuidStr);
            JsonObject field = sanctum.getEntry(fieldUuid);
            if (field != null && "totp".equals(field.getString("kind"))) {
                String code = sanctum.totpCode(fieldUuid);
                int remaining = 30 - (int) ((System.currentTimeMillis() / 1000) % 30);
                totpLabel.setText(code + "  (" + remaining + "s)");
            } else {
                totpLabel.setText("");
            }
        } catch (Exception ignore) {
            totpLabel.setText("");
        }
    }

    // ---- 设置页 ----

    private JPanel buildSettingsPanel() {
        JPanel box = new JPanel(new GridLayout(0, 1, 8, 8));
        box.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        box.add(new JLabel("主题"));
        JTextField themeField = new JTextField(config.theme());
        box.add(themeField);
        JButton saveTheme = new JButton("保存主题");
        saveTheme.addActionListener(e -> {
            config.setTheme(themeField.getText());
            applyTheme();
        });
        box.add(saveTheme);

        box.add(new JLabel("自动锁定（秒）"));
        JTextField lockField = new JTextField(String.valueOf(config.lockTimeoutSeconds()));
        box.add(lockField);
        JButton saveLock = new JButton("保存");
        saveLock.addActionListener(e -> {
            try {
                config.setLockTimeoutSeconds(Integer.parseInt(lockField.getText()));
            } catch (NumberFormatException ignore) {
            }
        });
        box.add(saveLock);

        box.add(new JLabel("剪贴板清空（秒）"));
        JTextField clipField = new JTextField(String.valueOf(config.clipboardClearSeconds()));
        box.add(clipField);
        JButton saveClip = new JButton("保存");
        saveClip.addActionListener(e -> {
            try {
                config.setClipboardClearSeconds(Integer.parseInt(clipField.getText()));
            } catch (NumberFormatException ignore) {
            }
        });
        box.add(saveClip);

        JButton back = new JButton("返回");
        back.addActionListener(e -> {
            if (sanctum != null && sanctum.isUnlocked()) {
                frame.setContentPane(buildMainPanel());
            } else {
                frame.setContentPane(buildUnlockPanel());
            }
            frame.revalidate();
        });
        box.add(back);
        return box;
    }
}
