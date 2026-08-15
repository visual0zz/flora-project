package com.flora.sanctum.app;

import com.flora.root.codec.json.model.JsonObject;
import com.flora.sanctum.config.UserConfig;
import com.flora.sanctum.model.Sanctum;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
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
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * flora-sanctum Swing GUI（完整可用的密码管理器桌面界面）。
 * <p>
 * 纯 JDK（Swing/AWT，java.desktop）。解锁屏（最近库列表 + 快速解锁）→
 * 三栏主界面（组树/条目列表/编辑面板），支持新建/删除条目与文件夹、字段增删与编辑、
 * 条目重命名、搜索、TOTP、复制、同步、设置、切换库、锁定。
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
    private final Map<UUID, DefaultMutableTreeNode> groupNodes = new LinkedHashMap<>();
    private JList<String> entryList;
    private DefaultListModel<String> entryModel;
    /** 与 entryModel 平行的条目 UUID 列表（UI 只显示名称，按索引定位 UUID）。 */
    private final List<UUID> entryUuids = new ArrayList<>();
    private JPanel editPanel;
    private JLabel statusLabel;
    private UUID selectedEntry;
    private String copiedPlaintext;
    private java.util.Timer autoLockTimer;
    private java.util.Timer clipboardTimer;
    private String openVaultPath;

    public static void launch(String[] args) {
        new SanctumGui().run(args);
    }

    private void run(String[] args) {
        applyTheme(config.theme());
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
            frame.setSize(520, 400);
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

    /** 应用 FlatLaf 主题（light/dark/system）。 */
    private void applyTheme(String theme) {
        try {
            switch (theme == null ? "system" : theme) {
                case "light" -> com.formdev.flatlaf.FlatLightLaf.setup();
                case "dark" -> com.formdev.flatlaf.FlatDarkLaf.setup();
                default -> javax.swing.UIManager.setLookAndFeel(
                        javax.swing.UIManager.getSystemLookAndFeelClassName());
            }
        } catch (Exception ignore) {
            // 主题安装失败则保留系统默认外观
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
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(16, 20, 16, 20));

        JLabel title = new JLabel("flora-sanctum");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        panel.add(title, BorderLayout.NORTH);

        // 中：最近库列表
        JLabel recentLabel = new JLabel("最近打开的库");
        recentLabel.setFont(recentLabel.getFont().deriveFont(Font.BOLD, 12f));
        JList<String> recentList = new JList<>(recentModel());
        recentList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        recentList.setVisibleRowCount(5);
        JScrollPane recentScroll = new JScrollPane(recentList);

        JPanel center = new JPanel(new BorderLayout(0, 6));
        center.add(recentLabel, BorderLayout.NORTH);
        center.add(recentScroll, BorderLayout.CENTER);

        JButton browseBtn = new JButton("打开其他库…");
        JPanel centerBottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        centerBottom.add(browseBtn);
        center.add(centerBottom, BorderLayout.SOUTH);
        panel.add(center, BorderLayout.CENTER);

        // 底：主密码 + 解锁
        JPanel bottom = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 0, 4, 6);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 0;
        bottom.add(new JLabel("主密码："), c);

        JPasswordField pwField = new JPasswordField();
        JLabel vaultName = new JLabel("");
        vaultName.setFont(vaultName.getFont().deriveFont(Font.ITALIC, 11f));
        c.gridx = 1;
        c.weightx = 1.0;
        bottom.add(pwField, c);

        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0;
        bottom.add(vaultName, c);

        JButton unlockBtn = new JButton("解锁 / 新建");
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 2;
        c.weightx = 1.0;
        bottom.add(unlockBtn, c);

        JLabel error = new JLabel();
        error.setForeground(java.awt.Color.RED.darker());
        c.gridy = 3;
        bottom.add(error, c);

        JLabel hint = new JLabel("选中库后直接输密码回车进入；路径不存在则新建库");
        hint.setFont(hint.getFont().deriveFont(Font.ITALIC, 11f));
        c.gridy = 4;
        bottom.add(hint, c);
        panel.add(bottom, BorderLayout.SOUTH);

        // 行为：选中最近库 → 预填路径并聚焦密码框
        recentList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            String sel = recentList.getSelectedValue();
            if (sel == null) {
                return;
            }
            Path p = Path.of(sel);
            vaultName.setText("库：" + p.getFileName());
            pwField.setText("");
            pwField.requestFocusInWindow();
        });

        browseBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                String path = chooser.getSelectedFile().getAbsolutePath();
                config.addRecentVault(path);
                recentList.setModel(recentModel());
                recentList.setSelectedValue(path, true);
            }
        });

        java.util.function.Consumer<String> unlock = sel -> {
            String path = sel != null ? sel
                    : (recentList.getSelectedValue() != null ? recentList.getSelectedValue() : null);
            if (path == null) {
                error.setText("请选择或打开一个库");
                return;
            }
            doUnlock(Path.of(path), pwField, error);
        };

        unlockBtn.addActionListener(e -> unlock.accept(null));
        pwField.addActionListener(e -> unlock.accept(null));

        // 预选上次打开的库
        String last = config.lastVault();
        if (last != null && recentList.getModel().getSize() > 0) {
            recentList.setSelectedValue(last, true);
        }
        return panel;
    }

    private DefaultListModel<String> recentModel() {
        DefaultListModel<String> m = new DefaultListModel<>();
        for (String p : config.recentVaults()) {
            m.addElement(p);
        }
        return m;
    }

    private void doUnlock(Path root, JPasswordField pwField, JLabel error) {
        char[] pw = pwField.getPassword();
        if (pw.length == 0) {
            error.setText("请输入主密码");
            return;
        }
        try {
            sanctum = Sanctum.open(root);
            try {
                sanctum.unlock(pw);
            } catch (IllegalArgumentException noManifest) {
                if (noManifest.getMessage() != null && noManifest.getMessage().contains("no manifest")) {
                    sanctum = Sanctum.createAndUnlock(root, pw);
                } else {
                    throw noManifest;
                }
            }
            openVaultPath = root.toAbsolutePath().toString();
            config.addRecentVault(openVaultPath);
            config.setLastVault(openVaultPath);
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
        openVaultPath = null;
        stopTimers();
        frame.setContentPane(buildUnlockPanel());
        frame.setSize(520, 400);
        frame.revalidate();
    }

    private void switchVault() {
        lock();
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
        JButton switchBtn = new JButton("切换库");
        JButton lockBtn = new JButton("锁定");
        statusLabel = new JLabel();
        syncBtn.setVisible(isFullyManaged());

        JTextField searchField = new JTextField(14);
        searchFieldRef = searchField;
        searchField.setToolTipText("按名称/字段搜索条目");
        JButton clearSearch = new JButton("×");
        clearSearch.setPreferredSize(new Dimension(26, 24));
        clearSearch.setMargin(new Insets(0, 0, 0, 0));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        top.add(newEntryBtn);
        top.add(newGroupBtn);
        top.add(delBtn);
        top.add(syncBtn);
        top.add(settingsBtn);
        top.add(switchBtn);
        top.add(lockBtn);
        top.add(new JLabel("搜索:"));
        top.add(searchField);
        top.add(clearSearch);
        top.add(statusLabel);
        root.add(top, BorderLayout.NORTH);

        JLabel vaultTitle = new JLabel("库：" + (openVaultPath == null ? "" : Path.of(openVaultPath).getFileName()));
        vaultTitle.setFont(vaultTitle.getFont().deriveFont(Font.BOLD, 13f));
        vaultTitle.setBorder(new EmptyBorder(4, 8, 4, 8));
        root.add(vaultTitle, BorderLayout.SOUTH);

        // 左：组树
        groupTree = new JTree();
        groupTree.setRootVisible(true);
        groupTree.setCellRenderer(new FolderTreeRenderer());
        rebuildGroupTree();
        groupTree.addTreeSelectionListener(e -> {
            resetAutoLock();
            refreshEntryList(searchField.getText());
        });
        JScrollPane treeScroll = new JScrollPane(groupTree);

        // 中：条目列表
        entryModel = new DefaultListModel<>();
        entryList = new JList<>(entryModel);
        entryList.setCellRenderer(new EntryListRenderer());
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
        switchBtn.addActionListener(e -> switchVault());
        lockBtn.addActionListener(e -> lock());
        searchField.addActionListener(e -> refreshEntryList(searchField.getText()));
        clearSearch.addActionListener(e -> {
            searchField.setText("");
            refreshEntryList("");
        });
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

    // ---- 条目列表 ----

    private void refreshEntryList(String filter) {
        entryModel.clear();
        entryUuids.clear();
        UUID groupId = currentGroupId();
        String q = filter == null ? "" : filter.trim().toLowerCase();
        for (UUID u : sanctum.listObjectUuids()) {
            JsonObject n = sanctum.getEntry(u);
            if (n == null || !"entry".equals(n.getString("type"))) {
                continue;
            }
            String p = n.getString("parent");
            boolean inGroup = (groupId == null && p == null) || (groupId != null && groupId.toString().equals(p));
            if (!inGroup) {
                continue;
            }
            if (!q.isEmpty() && !matchesFilter(u, q)) {
                continue;
            }
            entryModel.addElement(n.getString("name"));
            entryUuids.add(u);
        }
    }

    private boolean matchesFilter(UUID entryUuid, String q) {
        JsonObject entry = sanctum.getEntry(entryUuid);
        if (entry != null && entry.getString("name") != null
                && entry.getString("name").toLowerCase().contains(q)) {
            return true;
        }
        for (UUID f : sanctum.directory().childrenOf(entryUuid)) {
            JsonObject field = sanctum.getEntry(f);
            if (field != null) {
                if (field.getString("fieldName") != null
                        && field.getString("fieldName").toLowerCase().contains(q)) {
                    return true;
                }
                if (field.getString("value") != null
                        && field.getString("value").toLowerCase().contains(q)) {
                    return true;
                }
            }
        }
        return false;
    }

    private UUID currentGroupId() {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) groupTree.getLastSelectedPathComponent();
        return (node == null || node == treeRoot) ? null : (UUID) node.getUserObject();
    }

    private void showSelectedEntry() {
        UUID u = selectedEntryUuid();
        if (u == null) {
            selectedEntry = null;
            clearEditPanel();
            return;
        }
        selectedEntry = u;
        renderEntry(selectedEntry);
    }

    /** 当前选中条目（按列表索引从平行 UUID 列表解析）。 */
    private UUID selectedEntryUuid() {
        int idx = entryList.getSelectedIndex();
        if (idx < 0 || idx >= entryUuids.size()) {
            return null;
        }
        return entryUuids.get(idx);
    }

    private void clearEditPanel() {
        editPanel.removeAll();
        editPanel.revalidate();
        editPanel.repaint();
    }

    /** 渲染条目编辑面板（名称 + 各字段 + 增删 + TOTP + 复制 + 保存）。 */
    private void renderEntry(UUID entryUuid) {
        editPanel.removeAll();
        JsonObject entry = sanctum.getEntry(entryUuid);
        if (entry == null) {
            return;
        }
        // 名称：必填，可编辑
        JPanel nameRow = new JPanel(new BorderLayout(6, 0));
        JLabel nameTag = new JLabel("名称*");
        nameTag.setFont(nameTag.getFont().deriveFont(Font.BOLD, 14f));
        nameTag.setForeground(new java.awt.Color(180, 60, 60));
        JTextField nameField = new JTextField(entry.getString("name"));
        nameRow.add(nameTag, BorderLayout.WEST);
        nameRow.add(nameField, BorderLayout.CENTER);
        editPanel.add(nameRow);
        JLabel legend = new JLabel("名称与密码为必填，其余字段可选（× 删除可选字段）");
        legend.setFont(legend.getFont().deriveFont(Font.ITALIC, 10f));
        editPanel.add(legend);

        // 该条目的字段（parent=entryUuid 的 field）
        Map<UUID, JTextField> fieldInputs = new LinkedHashMap<>();
        List<UUID> fieldOrder = new ArrayList<>(sanctum.directory().childrenOf(entryUuid));
        for (UUID f : fieldOrder) {
            JsonObject field = sanctum.getEntry(f);
            if (field != null && "field".equals(field.getString("type"))) {
                String fn = field.getString("fieldName");
                String val = field.getString("value");
                String kind = field.getString("kind");
                JPanel row = new JPanel(new BorderLayout(6, 0));
                boolean isPassword = "password".equals(kind)
                        || (kind == null && "password".equals(fn));
                JLabel fLabel = new JLabel((isPassword ? "密码*" : fn) + " :");
                fLabel.setPreferredSize(new Dimension(110, 24));
                if (isPassword) {
                    fLabel.setForeground(new java.awt.Color(180, 60, 60));
                    fLabel.setFont(fLabel.getFont().deriveFont(Font.BOLD));
                }
                JTextField fValue = new JTextField(val == null ? "" : val);
                fValue.setToolTipText(kind == null ? "text" : kind);
                row.add(fLabel, BorderLayout.WEST);
                row.add(fValue, BorderLayout.CENTER);
                // 可选字段可删除；密码为必填，不提供删除
                if (!isPassword) {
                    JButton delField = new JButton("×");
                    delField.setToolTipText("删除字段 " + fn);
                    delField.setMargin(new Insets(0, 0, 0, 0));
                    delField.setPreferredSize(new Dimension(24, 24));
                    UUID fieldId = f;
                    delField.addActionListener(e -> {
                        resetAutoLock();
                        try {
                            sanctum.deleteField(fieldId);
                            renderEntry(entryUuid);
                            statusLabel.setText("字段已删除");
                        } catch (Exception ex) {
                            statusLabel.setText("删除失败");
                        }
                    });
                    row.add(delField, BorderLayout.EAST);
                }
                // TOTP 字段显示验证码
                if ("totp".equals(kind)) {
                    try {
                        String code = sanctum.totpCode(f);
                        JLabel totp = new JLabel("  验证码: " + code);
                        row.add(totp, BorderLayout.SOUTH);
                    } catch (Exception ignore) {
                    }
                }
                editPanel.add(row);
                fieldInputs.put(f, fValue);
            }
        }

        // 操作行：保存 / 添加字段 / 复制密码
        JButton saveBtn = new JButton("保存");
        saveBtn.addActionListener(e -> {
            String newName = nameField.getText().trim();
            if (newName.isEmpty()) {
                statusLabel.setText("条目名称必填");
                return;
            }
            // 密码为必填字段
            for (Map.Entry<UUID, JTextField> fe : fieldInputs.entrySet()) {
                JsonObject fobj = sanctum.getEntry(fe.getKey());
                if (fobj == null) {
                    continue;
                }
                String fkind = fobj.getString("kind");
                String ffn = fobj.getString("fieldName");
                boolean pwd = "password".equals(fkind) || (fkind == null && "password".equals(ffn));
                if (pwd && fe.getValue().getText().trim().isEmpty()) {
                    statusLabel.setText("密码必填");
                    return;
                }
            }
            if (!newName.equals(entry.getString("name"))) {
                try {
                    sanctum.renameEntry(entryUuid, newName);
                } catch (Exception ex) {
                    statusLabel.setText("重命名失败");
                }
            }
            saveFieldInputs(fieldInputs, entryUuid);
        });
        JButton addFieldBtn = new JButton("+ 添加字段");
        addFieldBtn.addActionListener(e -> addFieldDialog(entryUuid));
        JButton copyBtn = new JButton("复制密码");
        copyBtn.addActionListener(e -> copyPassword(entryUuid));
        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        actionRow.add(saveBtn);
        actionRow.add(addFieldBtn);
        actionRow.add(copyBtn);
        editPanel.add(actionRow);

        editPanel.revalidate();
        editPanel.repaint();
    }

    private void addFieldDialog(UUID entryUuid) {
        JTextField nameField = new JTextField(12);
        JTextField valField = new JTextField(18);
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 6));
        form.add(new JLabel("字段名:"));
        form.add(nameField);
        form.add(new JLabel("值:"));
        form.add(valField);
        int ok = JOptionPane.showConfirmDialog(frame, form, "添加字段", JOptionPane.OK_CANCEL_OPTION);
        if (ok == JOptionPane.OK_OPTION) {
            String fn = nameField.getText().trim();
            if (fn.isEmpty()) {
                statusLabel.setText("字段名不能为空");
                return;
            }
            resetAutoLock();
            try {
                UUID groupId = groupIdOf(entryUuid);
                sanctum.createFieldWithKind(entryUuid, groupId, fn, valField.getText(), null);
                renderEntry(entryUuid);
                statusLabel.setText("字段已添加");
            } catch (Exception ex) {
                statusLabel.setText("添加失败");
            }
        }
    }

    /** 由条目推导其所属组（新增字段需用同一 DEK）。 */
    private UUID groupIdOf(UUID entryUuid) {
        JsonObject entry = sanctum.getEntry(entryUuid);
        if (entry == null) {
            return null;
        }
        String p = entry.getString("parent");
        return p == null || p.isEmpty() ? null : UUID.fromString(p);
    }

    /** 保存按钮：逐个提交所有字段输入框的值，任一失败则记录并提示。 */
    private void saveFieldInputs(Map<UUID, JTextField> inputs, UUID entryUuid) {
        resetAutoLock();
        int failed = 0;
        for (Map.Entry<UUID, JTextField> e : inputs.entrySet()) {
            try {
                sanctum.updateField(e.getKey(), e.getValue().getText());
            } catch (Exception ex) {
                failed++;
            }
        }
        if (failed == 0) {
            statusLabel.setText("条目已保存");
            refreshEntryList(currentSearchQuery());
            rebuildGroupTree();
            renderEntry(entryUuid);
        } else {
            statusLabel.setText("保存失败: " + failed + " 个字段");
        }
    }

    private String currentSearchQuery() {
        return searchFieldRef == null ? "" : searchFieldRef.getText();
    }

    private JTextField searchFieldRef;

    private void copyPassword(UUID entryUuid) {
        resetAutoLock();
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
                if (copiedPlaintext != null) {
                    setClipboard("");
                    copiedPlaintext = null;
                }
            }
        }, config.clipboardClearSeconds() * 1000L);
    }

    // ================= 新建 / 删除 =================

    /**
     * 新建条目。必填：条目名称、密码；可选：用户名。
     * 用表单对话框一次录入，避免"空条目再补字段"的别扭流程。
     */
    private void doNewEntry() {
        UUID groupId = currentGroupId();
        JTextField nameField = new JTextField(16);
        JTextField userField = new JTextField(16);
        JPasswordField pwField = new JPasswordField(16);
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(label("名称 *"));
        form.add(nameField);
        form.add(label("用户名"));
        form.add(userField);
        form.add(label("密码 *"));
        form.add(pwField);
        int ok = JOptionPane.showConfirmDialog(frame, form, "新建条目", JOptionPane.OK_CANCEL_OPTION);
        if (ok != JOptionPane.OK_OPTION) {
            return;
        }
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            statusLabel.setText("条目名称必填");
            return;
        }
        char[] pw = pwField.getPassword();
        if (pw.length == 0) {
            statusLabel.setText("密码必填");
            java.util.Arrays.fill(pw, (char) 0);
            return;
        }
        Map<String, String> fields = new LinkedHashMap<>();
        String username = userField.getText().trim();
        if (!username.isEmpty()) {
            fields.put("username", username);
        }
        fields.put("password", new String(pw));
        java.util.Arrays.fill(pw, (char) 0);
        sanctum.createEntry(groupId, name, fields);
        refreshEntryList(currentSearchQuery());
        rebuildGroupTree();
        resetAutoLock();
        statusLabel.setText("已新建条目 " + name);
    }

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        if (text.endsWith(" *")) {
            l.setForeground(new java.awt.Color(180, 60, 60));
            l.setFont(l.getFont().deriveFont(Font.BOLD));
        }
        return l;
    }

    private void doNewGroup() {
        UUID parentId = currentGroupId();
        String name = JOptionPane.showInputDialog(frame, "文件夹名称:");
        if (name == null || name.isBlank()) {
            return;
        }
        sanctum.createGroup(parentId, name.trim());
        rebuildGroupTree();
        resetAutoLock();
    }

    private void doDelete() {
        UUID entryUuid = selectedEntryUuid();
        if (entryUuid != null) {
            int ok = JOptionPane.showConfirmDialog(frame, "删除该条目?", "确认", JOptionPane.YES_NO_OPTION);
            if (ok == JOptionPane.YES_OPTION) {
                sanctum.deleteEntry(entryUuid);
                refreshEntryList(currentSearchQuery());
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
                refreshEntryList(currentSearchQuery());
                resetAutoLock();
            }
        }
    }

    private void deleteGroupRecursive(UUID groupId) {
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
            refreshEntryList(currentSearchQuery());
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

    /** 组树渲染器：文件夹图标 + 组名。 */
    private static final class FolderTreeRenderer extends javax.swing.tree.DefaultTreeCellRenderer {
        @Override
        public java.awt.Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                               boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            setIcon(SvgIcon.get("folder", 16));
            setDisabledIcon(SvgIcon.get("folder", 16));
            return this;
        }
    }

    /** 条目列表渲染器：锁图标 + 条目名。 */
    private static final class EntryListRenderer extends javax.swing.DefaultListCellRenderer {
        @Override
        public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                               boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            setIcon(SvgIcon.get("entry", 16));
            return this;
        }
    }
}
