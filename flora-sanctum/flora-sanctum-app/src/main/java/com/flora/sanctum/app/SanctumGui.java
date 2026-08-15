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
import javax.swing.JTextArea;
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
        newEntryBtn = new JButton("新建条目");
        newGroupBtn = new JButton("新建文件夹");
        delBtn = new JButton("删除");
        syncBtn = new JButton("同步");
        settingsBtn = new JButton("设置");
        switchBtn = new JButton("切换库");
        lockBtn = new JButton("锁定");
        importImageBtn = new JButton("导入图片");
        addSshBtn = new JButton("添加 SSH 密钥");
        addRemoteBtn = new JButton("添加远程");
        statusLabel = new JLabel();
        syncBtn.setVisible(isFullyManaged());
        importImageBtn.setVisible(false);
        addSshBtn.setVisible(false);
        addRemoteBtn.setVisible(false);

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
        top.add(importImageBtn);
        top.add(addSshBtn);
        top.add(addRemoteBtn);
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
            updateToolbar();
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
        importImageBtn.addActionListener(e -> doImportImage());
        addSshBtn.addActionListener(e -> addSshKey());
        addRemoteBtn.addActionListener(e -> addRemote());
        searchField.addActionListener(e -> refreshEntryList(searchField.getText()));
        clearSearch.addActionListener(e -> {
            searchField.setText("");
            refreshEntryList("");
        });
        updateToolbar();
        return root;
    }

    /** 根据当前树选择切换工具栏按钮可见性。 */
    private void updateToolbar() {
        String section = sectionOf(currentSelection());
        boolean iconSec = ROOT_ICON.equals(section);
        boolean sshSec = ROOT_SSHKEY.equals(section);
        boolean remoteSec = ROOT_REMOTE.equals(section);
        importImageBtn.setVisible(iconSec);
        addSshBtn.setVisible(sshSec);
        addRemoteBtn.setVisible(remoteSec);
        // 新建条目/文件夹：仅密码库文件夹上下文可用
        boolean objectsCtx = section == null && currentGroupId() != null; // 选中了普通文件夹
        boolean objectsRoot = ROOT_OBJECTS.equals(section); // 密码库根（可建文件夹）
        newEntryBtn.setEnabled(objectsCtx);
        newGroupBtn.setEnabled(objectsCtx || objectsRoot);
        delBtn.setEnabled(true);
    }

    private boolean isFullyManaged() {
        return sanctum != null
                && new com.flora.sanctum.sync.SyncService(sanctum.root()).isFullyManaged();
    }

    // ---- 组树 ----

    /** 树节点类型：普通文件夹（UUID）或角色根/区段（字符串标记）。 */
    private static final String ROOT_OBJECTS = "ROOT_OBJECTS";
    private static final String ROOT_ICON = "ROOT_ICON";
    private static final String ROOT_SSHKEY = "ROOT_SSHKEY";
    private static final String ROOT_REMOTE = "ROOT_REMOTE";

    private void rebuildGroupTree() {
        treeRoot = new DefaultMutableTreeNode("全部");
        groupNodes.clear();
        groupCache = null; // 重置缓存

        // 三个根组 + 远程区段
        DefaultMutableTreeNode objectsNode = new DefaultMutableTreeNode("密码库");
        objectsNode.setUserObject(ROOT_OBJECTS);
        treeRoot.add(objectsNode);
        // objects 层级：用户顶层文件夹（parent 为 null 且无 role）+ 递归子文件夹
        for (Map.Entry<UUID, String[]> e : groupsById().entrySet()) {
            String parent = e.getValue()[0];
            String role = e.getValue()[2];
            if (role != null) {
                continue; // 三个 role 根组不参与 objects 层级
            }
            if (parent == null || !groupsById().containsKey(UUID.fromString(parent))) {
                addGroupNode(objectsNode, e.getKey(), e.getValue()[1]);
            }
        }

        DefaultMutableTreeNode iconNode = new DefaultMutableTreeNode("图标");
        iconNode.setUserObject(ROOT_ICON);
        treeRoot.add(iconNode);

        DefaultMutableTreeNode sshNode = new DefaultMutableTreeNode("SSH 密钥");
        sshNode.setUserObject(ROOT_SSHKEY);
        treeRoot.add(sshNode);

        DefaultMutableTreeNode remoteNode = new DefaultMutableTreeNode("远程");
        remoteNode.setUserObject(ROOT_REMOTE);
        treeRoot.add(remoteNode);

        groupTree.setModel(new DefaultTreeModel(treeRoot));
        groupTree.expandRow(0);
        groupTree.expandRow(1);
    }

    private void addGroupNode(DefaultMutableTreeNode parentNode, UUID id, String name) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(name == null ? "未命名" : name);
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

    /** group → {parent, name, role}，角色根组带 role 字段。 */
    private Map<UUID, String[]> groupsById() {
        if (groupCache == null) {
            groupCache = new LinkedHashMap<>();
            for (UUID u : sanctum.listObjectUuids()) {
                JsonObject n = sanctum.getEntry(u);
                if (n != null && "group".equals(n.getString("type"))) {
                    groupCache.put(u, new String[]{n.getString("parent"), n.getString("name"), n.getString("role")});
                }
            }
        }
        return groupCache;
    }

    // ---- 条目列表 ----

    private void refreshEntryList(String filter) {
        entryModel.clear();
        entryUuids.clear();
        String q = filter == null ? "" : filter.trim().toLowerCase();
        Object sel = currentSelection();
        String section = sectionOf(sel);
        UUID groupId = section == null ? groupIdOf(sel) : null;

        if (ROOT_ICON.equals(section)) {
            // 图标区段：列出所有 icon
            for (UUID u : sanctum.listObjectUuids()) {
                JsonObject n = sanctum.getEntry(u);
                if (n != null && "icon".equals(n.getString("type"))) {
                    entryModel.addElement(iconLabel(n));
                    entryUuids.add(u);
                }
            }
            return;
        }
        if (ROOT_SSHKEY.equals(section)) {
            for (UUID u : sanctum.listObjectUuids()) {
                JsonObject n = sanctum.getEntry(u);
                if (n != null && "sshKey".equals(n.getString("type"))) {
                    entryModel.addElement(n.getString("name"));
                    entryUuids.add(u);
                }
            }
            return;
        }
        if (ROOT_REMOTE.equals(section)) {
            for (UUID u : sanctum.listObjectUuids()) {
                JsonObject n = sanctum.getEntry(u);
                if (n != null && "field".equals(n.getString("type"))
                        && "remote".equals(n.getString("kind"))) {
                    entryModel.addElement(n.getString("fieldName"));
                    entryUuids.add(u);
                }
            }
            return;
        }

        // 密码库层级：列出当前文件夹下的条目
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

    /** 图标区段条目显示名（含格式/尺寸提示）。 */
    private String iconLabel(JsonObject icon) {
        String format = icon.getString("format");
        return (format == null ? "图标" : "图标 [" + format + "]");
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

    /** 当前树选中节点（null=未选）。 */
    private DefaultMutableTreeNode currentTreeNode() {
        return (DefaultMutableTreeNode) groupTree.getLastSelectedPathComponent();
    }

    /** 当前选中对象：文件夹 UUID，或区段标记字符串（ROOT_*），或 null。 */
    private Object currentSelection() {
        DefaultMutableTreeNode node = currentTreeNode();
        if (node == null || node == treeRoot) {
            return null;
        }
        return node.getUserObject();
    }

    /** 若当前选中是文件夹 UUID 则返回，否则 null。 */
    private UUID currentGroupId() {
        Object sel = currentSelection();
        return sel instanceof UUID u ? u : null;
    }

    /** 若当前选中是区段标记则返回，否则 null。 */
    private String sectionOf(Object sel) {
        return sel instanceof String s ? s : null;
    }

    private UUID groupIdOf(Object sel) {
        return sel instanceof UUID u ? u : null;
    }

    private void showSelectedEntry() {
        UUID u = selectedEntryUuid();
        if (u == null) {
            selectedEntry = null;
            clearEditPanel();
            return;
        }
        selectedEntry = u;
        String type = typeOf(u);
        if ("icon".equals(type)) {
            renderIconPanel(u);
        } else if ("sshKey".equals(type)) {
            renderSshKeyPanel(u);
        } else if ("field".equals(type)) {
            renderRemotePanel(u);
        } else {
            renderEntry(selectedEntry);
        }
    }

    /** 图标详情面板：预览图片 + 提示。 */
    private void renderIconPanel(UUID iconUuid) {
        editPanel.removeAll();
        JsonObject icon = sanctum.getEntry(iconUuid);
        editPanel.add(new JLabel("图标 [格式 " + (icon == null ? "?" : icon.getString("format")) + "]"));
        editPanel.add(new JLabel("自定义图标，可在条目编辑中选择使用"));
        editPanel.revalidate();
        editPanel.repaint();
    }

    /** SSH 密钥详情面板。 */
    private void renderSshKeyPanel(UUID keyUuid) {
        editPanel.removeAll();
        JsonObject key = sanctum.getEntry(keyUuid);
        editPanel.add(new JLabel("SSH 密钥：" + (key == null ? "?" : key.getString("name"))));
        editPanel.add(new JLabel("私钥已加密存储"));
        editPanel.revalidate();
        editPanel.repaint();
    }

    /** 远程配置详情面板。 */
    private void renderRemotePanel(UUID remoteUuid) {
        editPanel.removeAll();
        JsonObject remote = sanctum.getEntry(remoteUuid);
        editPanel.add(new JLabel("远程：" + (remote == null ? "?" : remote.getString("fieldName"))));
        editPanel.revalidate();
        editPanel.repaint();
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
        JButton iconBtn = new JButton("选择图标");
        iconBtn.addActionListener(e -> chooseEntryIcon(entryUuid));
        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        actionRow.add(saveBtn);
        actionRow.add(addFieldBtn);
        actionRow.add(copyBtn);
        actionRow.add(iconBtn);
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
    private JButton importImageBtn;
    private JButton addSshBtn;
    private JButton addRemoteBtn;
    private JButton newEntryBtn;
    private JButton newGroupBtn;
    private JButton delBtn;
    private JButton syncBtn;
    private JButton settingsBtn;
    private JButton switchBtn;
    private JButton lockBtn;

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

    // ================= 图标 / SSH / 远程 =================

    /** 导入图片文件为自定义图标（icon root）。 */
    private void doImportImage() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "图片 (png/jpg/gif/webp/svg)", "png", "jpg", "jpeg", "gif", "webp", "svg"));
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path file = chooser.getSelectedFile().toPath();
        resetAutoLock();
        try {
            byte[] data = Files.readAllBytes(file);
            String name = file.getFileName().toString();
            String format = extOf(name);
            if ("svg".equalsIgnoreCase(format)) {
                // SVG 文本直接存原始内容
                sanctum.createIcon(data, "svg");
            } else {
                javax.imageio.ImageIO.read(file.toFile()); // 校验确为可读图片
                sanctum.createIcon(data, format);
            }
            refreshEntryList(currentSearchQuery());
            statusLabel.setText("已导入图片 " + name);
        } catch (Exception ex) {
            statusLabel.setText("图片导入失败");
        }
    }

    private String extOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "png" : fileName.substring(dot + 1).toLowerCase();
    }

    /** 添加 SSH 私钥（sshKey root）。 */
    private void addSshKey() {
        JTextField nameField = new JTextField(16);
        JTextArea keyArea = new JTextArea(6, 30);
        keyArea.setLineWrap(true);
        keyArea.setWrapStyleWord(true);
        JPanel form = new JPanel(new BorderLayout(8, 8));
        JPanel topForm = new JPanel(new GridLayout(0, 2, 8, 6));
        topForm.add(new JLabel("名称:"));
        topForm.add(nameField);
        form.add(topForm, BorderLayout.NORTH);
        form.add(new JScrollPane(keyArea), BorderLayout.CENTER);
        int ok = JOptionPane.showConfirmDialog(frame, form, "添加 SSH 密钥", JOptionPane.OK_CANCEL_OPTION);
        if (ok != JOptionPane.OK_OPTION) {
            return;
        }
        String name = nameField.getText().trim();
        String pem = keyArea.getText().trim();
        if (name.isEmpty() || pem.isEmpty()) {
            statusLabel.setText("名称与私钥必填");
            return;
        }
        resetAutoLock();
        try {
            sanctum.createSshKey(name, pem);
            refreshEntryList(currentSearchQuery());
            statusLabel.setText("已添加 SSH 密钥 " + name);
        } catch (Exception ex) {
            statusLabel.setText("SSH 密钥添加失败");
        }
    }

    /** 添加远程配置（kind:remote，置于 objects root 下）。 */
    private void addRemote() {
        JTextField nameField = new JTextField(16);
        JTextField urlField = new JTextField(28);
        JTextField keyRefField = new JTextField(16);
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("名称:"));
        form.add(nameField);
        form.add(new JLabel("URL:"));
        form.add(urlField);
        form.add(new JLabel("SSH 密钥引用:"));
        form.add(keyRefField);
        int ok = JOptionPane.showConfirmDialog(frame, form, "添加远程", JOptionPane.OK_CANCEL_OPTION);
        if (ok != JOptionPane.OK_OPTION) {
            return;
        }
        String name = nameField.getText().trim();
        String url = urlField.getText().trim();
        if (name.isEmpty() || url.isEmpty()) {
            statusLabel.setText("名称与 URL 必填");
            return;
        }
        String keyRef = keyRefField.getText().trim();
        resetAutoLock();
        try {
            sanctum.createRemote(name, url, keyRef.isEmpty() ? null : keyRef);
            refreshEntryList(currentSearchQuery());
            statusLabel.setText("已添加远程 " + name);
        } catch (Exception ex) {
            statusLabel.setText("远程添加失败");
        }
    }

    /** 为条目选择一个已导入的自定义图标（或清除图标）。 */
    private void chooseEntryIcon(UUID entryUuid) {
        List<UUID> iconUuids = new ArrayList<>();
        for (UUID u : sanctum.listObjectUuids()) {
            JsonObject n = sanctum.getEntry(u);
            if (n != null && "icon".equals(n.getString("type"))) {
                iconUuids.add(u);
            }
        }
        if (iconUuids.isEmpty()) {
            statusLabel.setText("请先在\"图标\"区导入图片");
            return;
        }
        String[] choices = iconUuids.stream()
                .map(u -> iconLabel(sanctum.getEntry(u)) + "  [" + u + "]")
                .toArray(String[]::new);
        Object chosen = JOptionPane.showInputDialog(frame, "选择图标:", "条目图标",
                JOptionPane.PLAIN_MESSAGE, null, choices, choices[0]);
        if (chosen == null) {
            return;
        }
        String sel = chosen.toString();
        int idx = sel.lastIndexOf('[');
        String uuidStr = sel.substring(idx + 1, sel.length() - 1);
        resetAutoLock();
        try {
            sanctum.setEntryIcon(entryUuid, UUID.fromString(uuidStr));
            renderEntry(entryUuid);
            statusLabel.setText("已设置图标");
        } catch (Exception ex) {
            statusLabel.setText("设置图标失败");
        }
    }

    // ================= 新建 / 删除 =================

    /**
     * 新建条目。必填：条目名称、密码；可选：用户名。
     * 用表单对话框一次录入，避免"空条目再补字段"的别扭流程。
     */
    private void doNewEntry() {
        UUID groupId = currentGroupId();
        if (groupId == null) {
            statusLabel.setText("请先在密码库中选中一个文件夹");
            return;
        }
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
        Object sel = currentSelection();
        // 仅在密码库根(ROOT_OBJECTS)或普通文件夹下允许新建文件夹
        String section = sectionOf(sel);
        if (ROOT_ICON.equals(section) || ROOT_SSHKEY.equals(section) || ROOT_REMOTE.equals(section)) {
            statusLabel.setText("该区段不允许新建文件夹");
            return;
        }
        UUID parentId = groupIdOf(sel);
        String name = JOptionPane.showInputDialog(frame, "文件夹名称:");
        if (name == null || name.isBlank()) {
            return;
        }
        sanctum.createGroup(parentId, name.trim());
        rebuildGroupTree();
        resetAutoLock();
    }

    private void doDelete() {
        Object sel = currentSelection();
        String section = sectionOf(sel);
        UUID entryUuid = selectedEntryUuid();
        if (entryUuid != null) {
            String type = typeOf(entryUuid);
            String what = switch (type == null ? "" : type) {
                case "icon" -> "该图标";
                case "sshKey" -> "该 SSH 密钥";
                case "field" -> "该远程配置";
                default -> "该条目";
            };
            int ok = JOptionPane.showConfirmDialog(frame, "删除" + what + "?", "确认", JOptionPane.YES_NO_OPTION);
            if (ok == JOptionPane.YES_OPTION) {
                sanctum.deleteEntry(entryUuid);
                refreshEntryList(currentSearchQuery());
                resetAutoLock();
            }
            return;
        }
        // 区段根节点（图标/SSH/远程/密码库根）不可删除
        if (section != null) {
            statusLabel.setText("根组不允许删除");
            return;
        }
        DefaultMutableTreeNode node = currentTreeNode();
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

    /** 对象类型（entry/group/field/icon/sshKey），未知返回 null。 */
    private String typeOf(UUID uuid) {
        JsonObject n = sanctum.getEntry(uuid);
        return n == null ? null : n.getString("type");
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
