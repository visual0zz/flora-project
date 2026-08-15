package com.flora.sanctum.app;

import com.flora.root.codec.json.model.JsonObject;
import com.flora.sanctum.config.UserConfig;
import com.flora.sanctum.model.Sanctum;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * flora-sanctum Swing GUI（完整可用的密码管理器桌面界面）。
 * <p>
 * 纯 JDK（Swing/AWT，java.desktop）。解锁屏 → 三栏主界面（组树/条目列表/编辑面板），
 * 支持新建/删除条目与文件夹、字段编辑、TOTP、复制、同步、设置、锁定。
 * UI 只调用 core 公开 API（见设计 07），不解密、不碰 Git。
 */
public final class SanctumGui {

    private final java.util.concurrent.atomic.AtomicReference<Sanctum> current =
            new java.util.concurrent.atomic.AtomicReference<>();
    private final UserConfig config = new UserConfig();
    private com.flora.sanctum.server.SanctumHttpServer httpServer;
    private Sanctum sanctum;
    private JFrame frame;
    private JTree groupTree;
    private DefaultMutableTreeNode treeRoot;
    private final Map<UUID, DefaultMutableTreeNode> groupNodes = new HashMap<>();
    private JList<String> entryList;
    private javax.swing.DefaultListModel<String> entryModel;
    private JPanel editPanel;
    private JLabel statusLabel;
    private UUID selectedEntry;
    private String copiedPlaintext;
    private java.util.Timer autoLockTimer;
    private java.util.Timer clipboardTimer;

    /** 入口（无参走 GUI）。 */
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
            frame.setContentPane(buildUnlockPanel());
            frame.setSize(440, 320);
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

    // ---- 系统托盘 ----

    private void installTray() {
        if (!java.awt.SystemTray.isSupported()) {
            return;
        }
        java.awt.TrayIcon icon = new java.awt.TrayIcon(
                Toolkit.getDefaultToolkit().createImage(new byte[0]), "flora-sanctum");
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

    // ================= 解锁屏 =================

    private JPanel buildUnlockPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 12, 6, 12);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;

        JLabel title = new JLabel("flora-sanctum");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        c.gridy = 0;
        panel.add(title, c);

        JLabel dirLabel = new JLabel("库路径：");
        c.gridy = 1;
        panel.add(dirLabel, c);
        JPanel pathRow = new JPanel(new BorderLayout(4, 0));
        JTextField pathField = new JTextField();
        JButton browseBtn = new JButton("浏览…");
        browseBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                pathField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        pathRow.add(pathField, BorderLayout.CENTER);
        pathRow.add(browseBtn, BorderLayout.EAST);
        c.gridy = 2;
        panel.add(pathRow, c);

        JLabel pwLabel = new JLabel("主密码：");
        c.gridy = 3;
        panel.add(pwLabel, c);
        JPasswordField pwField = new JPasswordField();
        c.gridy = 4;
        panel.add(pwField, c);

        JLabel hint = new JLabel("路径不存在则新建库；空文件夹将初始化新库");
        hint.setFont(hint.getFont().deriveFont(Font.ITALIC, 11f));
        c.gridy = 5;
        panel.add(hint, c);

        JButton unlockBtn = new JButton("解锁 / 新建");
        c.gridy = 6;
        panel.add(unlockBtn, c);

        JLabel error = new JLabel();
        error.setForeground(java.awt.Color.RED.darker());
        c.gridy = 7;
        panel.add(error, c);

        unlockBtn.addActionListener(e -> doUnlock(pathField, pwField, error));
        pwField.addActionListener(e -> doUnlock(pathField, pwField, error));
        return panel;
    }

    private void doUnlock(JTextField pathField, JPasswordField pwField, JLabel error) {
        String path = pathField.getText().trim();
        if (path.isEmpty()) {
            error.setText("请输入库路径");
            return;
        }
        char[] pw = pwField.getPassword();
        try {
            Path root = Path.of(path);
            if (Files.isDirectory(root) && Files.list(root).findAny().isPresent()) {
                // 已有内容 → 打开并解锁
                sanctum = Sanctum.open(root);
                sanctum.unlock(pw);
            } else {
                // 不存在或空文件夹 → 初始化新库
                sanctum = Sanctum.createAndUnlock(root, pw);
            }
            current.set(sanctum);
            frame.setContentPane(buildMainPanel());
            frame.setSize(960, 640);
            frame.revalidate();
            startAutoLockTimer();
        } catch (Exception ex) {
            // 统一提示"解锁失败"，不区分密码错/数据损坏（见设计 03）
            error.setText("解锁失败");
        } finally {
            java.util.Arrays.fill(pw, (char) 0);
        }
    }

    // ================= 自动锁定 =================

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
        frame.setSize(440, 320);
        frame.revalidate();
    }

    // ================= 主界面 =================

    private JPanel buildMainPanel() {
        JPanel root = new JPanel(new BorderLayout());

        // 顶部工具栏
        JButton newEntryBtn = new JButton("新建条目");
        JButton newGroupBtn = new JButton("新建文件夹");
        JButton delBtn = new JButton("删除");
        JButton syncBtn = new JButton("同步");
        JButton settingsBtn = new JButton("设置");
        JButton lockBtn = new JButton("锁定");
        statusLabel = new JLabel();
        syncBtn.setVisible(isFullyManaged());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        top.add(newEntryBtn);
        top.add(newGroupBtn);
        top.add(delBtn);
        top.add(syncBtn);
        top.add(settingsBtn);
        top.add(lockBtn);
        top.add(statusLabel);
        root.add(top, BorderLayout.NORTH);

        // 左：组树
        groupTree = new JTree();
        groupTree.setRootVisible(true);
        rebuildGroupTree();
        groupTree.addTreeSelectionListener(e -> {
            resetAutoLock();
            refreshEntryList();
        });
        JScrollPane treeScroll = new JScrollPane(groupTree);

        // 中：条目列表
        entryModel = new javax.swing.DefaultListModel<>();
        entryList = new JList<>(entryModel);
        entryList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                resetAutoLock();
                showSelectedEntry();
            }
        });
        JScrollPane entryScroll = new JScrollPane(entryList);

        // 右：编辑面板
        editPanel = new JPanel();
        editPanel.setLayout(new BoxLayout(editPanel, BoxLayout.Y_AXIS));
        editPanel.setBorder(BorderFactory.createTitledBorder("条目编辑"));
        JScrollPane editScroll = new JScrollPane(editPanel);

        JSplitPane rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, entryScroll, editScroll);
        rightSplit.setDividerLocation(280);
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScroll, rightSplit);
        mainSplit.setDividerLocation(220);
        root.add(mainSplit, BorderLayout.CENTER);

        newEntryBtn.addActionListener(e -> doNewEntry());
        newGroupBtn.addActionListener(e -> doNewGroup());
        delBtn.addActionListener(e -> doDelete());
        syncBtn.addActionListener(e -> doSync());
        settingsBtn.addActionListener(e -> openSettings());
        lockBtn.addActionListener(e -> lock());
        return root;
    }

    private boolean isFullyManaged() {
        return sanctum != null
                && new com.flora.sanctum.sync.SyncService(sanctum.root()).isFullyManaged();
    }

    // ---- 组树 ----

    private void rebuildGroupTree() {
        treeRoot = new DefaultMutableTreeNode("全部");
        groupNodes.clear();
        groupCache = null; // 重置缓存
        // 递归建树（顶层 parent=null 或指向不存在的组）
        for (Map.Entry<UUID, String[]> e : groupsById().entrySet()) {
            String parent = e.getValue()[0];
            if (parent == null || !groupsById().containsKey(UUID.fromString(parent))) {
                addGroupNode(treeRoot, e.getKey(), e.getValue()[1]);
            }
        }
        groupTree.setModel(new DefaultTreeModel(treeRoot));
        groupTree.expandRow(0);
    }

    private void addGroupNode(DefaultMutableTreeNode parentNode, UUID id, String name) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(name == null ? id.toString() : name);
        node.setUserObject(id);
        parentNode.add(node);
        groupNodes.put(id, node);
        // 递归子组
        for (Map.Entry<UUID, String[]> e : groupsById().entrySet()) {
            String parent = e.getValue()[0];
            if (parent != null && parent.equals(id.toString())) {
                addGroupNode(node, e.getKey(), e.getValue()[1]);
            }
        }
    }

    private Map<UUID, String[]> groupCache;

    private Map<UUID, String[]> groupsById() {
        if (groupCache == null) {
            groupCache = new LinkedHashMap<>();
            for (UUID u : sanctum.listObjectUuids()) {
                JsonObject n = sanctum.getEntry(u);
                if (n != null && "group".equals(n.getString("type"))) {
                    groupCache.put(u, new String[]{n.getString("parent"), n.getString("name")});
                }
            }
        }
        return groupCache;
    }

    private void refreshEntryList() {
        entryModel.clear();
        selectedEntry = null;
        clearEditPanel();
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) groupTree.getLastSelectedPathComponent();
        UUID groupId = node == null || node == treeRoot ? null : (UUID) node.getUserObject();
        for (UUID u : sanctum.listObjectUuids()) {
            JsonObject n = sanctum.getEntry(u);
            if (n != null && "entry".equals(n.getString("type"))) {
                String p = n.getString("parent");
                boolean match = (groupId == null && p == null) || (groupId != null && groupId.toString().equals(p));
                if (match) {
                    entryModel.addElement(n.getString("name") + "  [" + u + "]");
                }
            }
        }
    }

    private void showSelectedEntry() {
        String sel = entryList.getSelectedValue();
        if (sel == null) {
            selectedEntry = null;
            clearEditPanel();
            return;
        }
        String uuidStr = sel.substring(sel.lastIndexOf('[') + 1, sel.lastIndexOf(']'));
        selectedEntry = UUID.fromString(uuidStr);
        renderEntry(selectedEntry);
    }

    private void clearEditPanel() {
        editPanel.removeAll();
        editPanel.revalidate();
        editPanel.repaint();
    }

    /** 渲染条目编辑面板（名称 + 各字段 + TOTP + 复制）。 */
    private void renderEntry(UUID entryUuid) {
        editPanel.removeAll();
        JsonObject entry = sanctum.getEntry(entryUuid);
        if (entry == null) {
            return;
        }
        JLabel nameLabel = new JLabel("名称: " + entry.getString("name"));
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 14f));
        editPanel.add(nameLabel);

        // 该条目的字段（parent=entryUuid 的 field）
        for (UUID f : sanctum.directory().childrenOf(entryUuid)) {
            JsonObject field = sanctum.getEntry(f);
            if (field != null && "field".equals(field.getString("type"))) {
                String fn = field.getString("fieldName");
                String val = field.getString("value");
                String kind = field.getString("kind");
                JPanel row = new JPanel(new BorderLayout(4, 0));
                JLabel fLabel = new JLabel(fn + " :");
                JTextField fValue = new JTextField(val == null ? "" : val);
                fValue.setToolTipText(kind == null ? "text" : kind);
                row.add(fLabel, BorderLayout.WEST);
                row.add(fValue, BorderLayout.CENTER);
                // 保存值
                String finalFn = fn;
                UUID fieldId = f;
                fValue.addActionListener(e -> {
                    resetAutoLock();
                    try {
                        sanctum.updateField(fieldId, fValue.getText());
                        statusLabel.setText("字段 " + finalFn + " 已保存");
                    } catch (Exception ex) {
                        statusLabel.setText("保存失败");
                    }
                });
                // TOTP 字段显示验证码
                if ("totp".equals(kind)) {
                    try {
                        String code = sanctum.totpCode(f);
                        JLabel totp = new JLabel("  验证码: " + code);
                        row.add(totp, BorderLayout.EAST);
                    } catch (Exception ignore) {
                    }
                }
                editPanel.add(row);
            }
        }

        JButton copyBtn = new JButton("复制密码");
        copyBtn.addActionListener(e -> copyPassword(entryUuid));
        editPanel.add(copyBtn);

        editPanel.revalidate();
        editPanel.repaint();
    }

    private void copyPassword(UUID entryUuid) {
        resetAutoLock();
        // 复制第一个 password 字段（或第一个字段）
        for (UUID f : sanctum.directory().childrenOf(entryUuid)) {
            JsonObject field = sanctum.getEntry(f);
            if (field != null && "field".equals(field.getString("type"))) {
                String kind = field.getString("kind");
                String val = field.getString("value");
                if ("password".equals(kind) || (kind == null && "password".equals(field.getString("fieldName")))) {
                    setClipboard(val == null ? "" : val);
                    copiedPlaintext = val;
                    startClipboardTimer();
                    statusLabel.setText("已复制");
                    return;
                }
            }
        }
        statusLabel.setText("未找到密码字段");
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

    // ================= 新建 / 删除 =================

    private void doNewEntry() {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) groupTree.getLastSelectedPathComponent();
        UUID groupId = (node == null || node == treeRoot) ? null : (UUID) node.getUserObject();
        String name = JOptionPane.showInputDialog(frame, "条目名称:");
        if (name == null || name.isBlank()) {
            return;
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("username", "");
        fields.put("password", "");
        sanctum.createEntry(groupId, name.trim(), fields);
        refreshEntryList();
        rebuildGroupTree();
        resetAutoLock();
    }

    private void doNewGroup() {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) groupTree.getLastSelectedPathComponent();
        UUID parentId = (node == null || node == treeRoot) ? null : (UUID) node.getUserObject();
        String name = JOptionPane.showInputDialog(frame, "文件夹名称:");
        if (name == null || name.isBlank()) {
            return;
        }
        sanctum.createGroup(parentId, name.trim());
        rebuildGroupTree();
        resetAutoLock();
    }

    private void doDelete() {
        String sel = entryList.getSelectedValue();
        if (sel != null) {
            String uuidStr = sel.substring(sel.lastIndexOf('[') + 1, sel.lastIndexOf(']'));
            int ok = JOptionPane.showConfirmDialog(frame, "删除该条目?", "确认", JOptionPane.YES_NO_OPTION);
            if (ok == JOptionPane.YES_OPTION) {
                sanctum.deleteEntry(UUID.fromString(uuidStr));
                refreshEntryList();
                resetAutoLock();
            }
            return;
        }
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) groupTree.getLastSelectedPathComponent();
        if (node != null && node != treeRoot && node.getUserObject() instanceof UUID groupId) {
            int ok = JOptionPane.showConfirmDialog(frame, "删除该文件夹及其内容?", "确认", JOptionPane.YES_NO_OPTION);
            if (ok == JOptionPane.YES_OPTION) {
                deleteGroupRecursive(groupId);
                rebuildGroupTree();
                refreshEntryList();
                resetAutoLock();
            }
        }
    }

    private void deleteGroupRecursive(UUID groupId) {
        // 删除子组 + 组内条目
        for (UUID child : sanctum.directory().childrenOf(groupId)) {
            JsonObject n = sanctum.getEntry(child);
            if (n != null && "group".equals(n.getString("type"))) {
                deleteGroupRecursive(child);
            } else {
                sanctum.deleteEntry(child);
            }
        }
        sanctum.deleteEntry(groupId);
    }

    // ================= 同步 =================

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
            rebuildGroupTree();
            refreshEntryList();
            statusLabel.setText("已同步");
        } catch (Exception e) {
            statusLabel.setText("同步失败");
        }
    }

    // ================= 设置 =================

    private void openSettings() {
        JDialog dialog = new JDialog(frame, "设置", true);
        JPanel box = new JPanel(new GridLayout(0, 1, 8, 8));
        box.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        box.add(new JLabel("主题（light/dark/system）"));
        JTextField themeField = new JTextField(config.theme());
        box.add(themeField);
        JButton saveTheme = new JButton("保存主题");
        saveTheme.addActionListener(e -> config.setTheme(themeField.getText()));
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

        JButton close = new JButton("关闭");
        close.addActionListener(e -> dialog.dispose());
        box.add(close);

        dialog.setContentPane(box);
        dialog.setSize(320, 320);
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }
}
