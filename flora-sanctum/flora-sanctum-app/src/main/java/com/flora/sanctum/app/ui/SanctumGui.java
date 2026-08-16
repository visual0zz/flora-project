package com.flora.sanctum.app.ui;

import com.flora.sanctum.config.UserConfig;
import com.flora.sanctum.model.tree.DataTree;
import com.flora.sanctum.model.tree.EntryNode;
import com.flora.sanctum.model.FieldKind;
import com.flora.sanctum.model.tree.FieldNode;
import com.flora.sanctum.model.tree.GroupNode;
import com.flora.sanctum.model.tree.IconNode;
import com.flora.sanctum.model.tree.RemoteNode;
import com.flora.sanctum.model.RootTag;
import com.flora.sanctum.model.Sanctum;
import com.flora.sanctum.model.tree.SshKeyNode;
import com.flora.sanctum.model.tree.TreeNode;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
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
    private com.flora.sanctum.app.server.SanctumHttpServer httpServer;
    private Sanctum sanctum;
    private JFrame frame;
    private JTree groupTree;
    private DefaultMutableTreeNode treeRoot;
    private final Map<UUID, DefaultMutableTreeNode> groupNodes = new LinkedHashMap<>();
    private JList<String> entryList;
    private DefaultListModel<String> entryModel;
    /** 与 entryModel 平行的条目 UUID 列表（UI 只显示名称，按索引定位 UUID）。 */
    private final List<UUID> entryUuids = new ArrayList<>();
    /** 与 entryUuids 平行的列表项类型（group/entry/icon/sshKey/field），供图标与双击导航。 */
    private final List<String> listItemTypes = new ArrayList<>();
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
            httpServer = new com.flora.sanctum.app.server.SanctumHttpServer(current::get, 0);
            httpServer.start();
        } catch (IOException e) {
            throw new IllegalStateException("cannot start HTTP server", e);
        }
        SwingUtilities.invokeLater(() -> {
            frame = new JFrame("flora-sanctum");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            applyMacTitleBar();
            installTray();
            frame.setContentPane(buildUnlockPanel());
            frame.setSize(520, 400);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            // Windows 标题栏颜色需窗口已显示后才能取到 HWND
            SwingUtilities.invokeLater(this::applyWindowsTitleBar);
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
            boolean dark = switch (theme == null ? "system" : theme) {
                case "light" -> false;
                case "dark" -> true;
                default -> isSystemDark();
            };
            if (dark) {
                com.formdev.flatlaf.FlatDarkLaf.setup();
            } else {
                com.formdev.flatlaf.FlatLightLaf.setup();
                applyPaperTheme();
            }
        } catch (Exception ignore) {
            // 主题安装失败则保留系统默认外观
        }
    }

    /** 探测操作系统明暗偏好（macOS 为准，其余平台返回 false）。 */
    private static boolean isSystemDark() {
        try {
            Object mode = java.awt.Toolkit.getDefaultToolkit()
                    .getDesktopProperty("apple.awt.appearance");
            return mode != null && String.valueOf(mode).toLowerCase().contains("dark");
        } catch (Exception ignore) {
            return false;
        }
    }

    /** macOS：标题栏颜色由系统默认（避免 draggableWindowBackground 劫持 JSplitPane divider 拖动）。 */
    private void applyMacTitleBar() {
        // 故意为空：保留 macOS 系统标题栏（含红黄绿按钮 + 拖动）不被自定义行为劫持
    }

    /** Windows：Win11 DWM 设置标题栏颜色（DWMWA_CAPTION_COLOR）；旧版本忽略。 */
    private void applyWindowsTitleBar() {
        if (!isWindows() || !isWin11()) {
            return;
        }
        try {
            com.sun.jna.platform.win32.WinDef.HWND hwnd =
                    new com.sun.jna.platform.win32.WinDef.HWND(com.sun.jna.Native.getComponentPointer(frame));
            // DWMWA_CAPTION_COLOR = 35；COLORREF = 0x00BBGGRR（R 在低字节）。偏暖黄 #F5EBD0
            int color = 0xF5 | (0xEB << 8) | (0xD0 << 16);
            com.sun.jna.Memory mem = new com.sun.jna.Memory(4);
            mem.setInt(0, color);
            Dwm.INSTANCE.DwmSetWindowAttribute(hwnd, 35, mem, 4);
        } catch (Throwable ignore) {
            // 不支持则保留系统默认标题栏
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /** Windows 11 build >= 22000 才支持 DWMWA_CAPTION_COLOR。 */
    private static boolean isWin11() {
        String v = System.getProperty("os.version", "");
        int dot = v.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        try {
            return Integer.parseInt(v.substring(dot + 1)) >= 22000;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** dwmapi.dll 最小绑定（DwmSetWindowAttribute 用于标题栏颜色）。 */
    private interface Dwm extends com.sun.jna.win32.StdCallLibrary {
        Dwm INSTANCE = com.sun.jna.Native.load("dwmapi", Dwm.class);

        int DwmSetWindowAttribute(com.sun.jna.platform.win32.WinDef.HWND hwnd,
                                  int attribute, com.sun.jna.Pointer value, int size);
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    /** 白灰偏暖黄色调：仿 CodeBuddy 极简白灰，但背景略偏纸黄。 */
    private void applyPaperTheme() {
        java.awt.Color paper = new java.awt.Color(0xF8, 0xF4, 0xE9);
        java.awt.Color paperLight = new java.awt.Color(0xF1, 0xED, 0xE1); // 按钮/下拉等控件底色（暖浅）
        java.awt.Color fieldWhite = new java.awt.Color(0xFF, 0xFF, 0xFF); // 编辑框纯白
        java.awt.Color ink = new java.awt.Color(0x5A, 0x55, 0x4D); // 文字图标淡灰棕
        java.awt.Color divider = new java.awt.Color(0xD8, 0xD2, 0xC0);
        java.awt.Color selectionBg = new java.awt.Color(0xE4, 0xDD, 0xC9); // 暖灰棕选中（焦点/失焦一致）
        javax.swing.UIManager.put("Panel.background", paper);
        javax.swing.UIManager.put("Panel.foreground", ink);
        javax.swing.UIManager.put("Label.foreground", ink);
        javax.swing.UIManager.put("Component.background", paper);
        javax.swing.UIManager.put("Component.foreground", ink);
        javax.swing.UIManager.put("TextField.background", fieldWhite);
        javax.swing.UIManager.put("TextField.foreground", ink);
        javax.swing.UIManager.put("TextArea.background", fieldWhite);
        javax.swing.UIManager.put("TextArea.foreground", ink);
        javax.swing.UIManager.put("PasswordField.background", fieldWhite);
        javax.swing.UIManager.put("PasswordField.foreground", ink);
        javax.swing.UIManager.put("Tree.background", paper);
        javax.swing.UIManager.put("Tree.foreground", ink);
        javax.swing.UIManager.put("Tree.selectionBackground", selectionBg);
        javax.swing.UIManager.put("Tree.selectionInactiveBackground", selectionBg);
        javax.swing.UIManager.put("Tree.selectionForeground", ink);
        javax.swing.UIManager.put("List.background", paper);
        javax.swing.UIManager.put("List.foreground", ink);
        javax.swing.UIManager.put("List.selectionBackground", selectionBg);
        javax.swing.UIManager.put("List.selectionInactiveBackground", selectionBg);
        javax.swing.UIManager.put("List.selectionForeground", ink);
        javax.swing.UIManager.put("Table.background", paper);
        javax.swing.UIManager.put("Table.foreground", ink);
        javax.swing.UIManager.put("Table.selectionBackground", selectionBg);
        javax.swing.UIManager.put("Table.selectionInactiveBackground", selectionBg);
        javax.swing.UIManager.put("Table.selectionForeground", ink);
        javax.swing.UIManager.put("Viewport.background", paper);
        javax.swing.UIManager.put("ScrollPane.background", paper);
        javax.swing.UIManager.put("ScrollPane.border", javax.swing.BorderFactory.createEmptyBorder());
        javax.swing.UIManager.put("Button.background", paperLight);
        javax.swing.UIManager.put("Button.foreground", ink);
        javax.swing.UIManager.put("ComboBox.background", paperLight);
        javax.swing.UIManager.put("ComboBox.foreground", ink);
        javax.swing.UIManager.put("Spinner.background", paperLight);
        javax.swing.UIManager.put("Spinner.foreground", ink);
        javax.swing.UIManager.put("ToolBar.background", paper);
        javax.swing.UIManager.put("ToolBar.border", javax.swing.BorderFactory.createEmptyBorder());
        javax.swing.UIManager.put("SplitPane.dividerSize", 1);
        javax.swing.UIManager.put("SplitPane.background", divider);
        javax.swing.UIManager.put("SplitPaneDivider.border",
                javax.swing.BorderFactory.createLineBorder(divider));
        javax.swing.UIManager.put("TitledBorder.border",
                javax.swing.BorderFactory.createLineBorder(divider));
        javax.swing.UIManager.put("TitledBorder.titleColor", ink);
        javax.swing.UIManager.put("TableHeader.background", paperLight);
        javax.swing.UIManager.put("TableHeader.foreground", ink);
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
        JPanel panel = new PaperPanel(new BorderLayout());
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
        recentList.setOpaque(false);
        JScrollPane recentScroll = new JScrollPane(recentList);
        recentScroll.setOpaque(false);
        recentScroll.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        recentScroll.getViewport().setOpaque(false);

        JPanel center = new JPanel(new BorderLayout(0, 6));
        center.setOpaque(false);
        center.add(recentLabel, BorderLayout.NORTH);
        center.add(recentScroll, BorderLayout.CENTER);

        JButton browseBtn = new JButton("打开其他库…");
        JPanel centerBottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        centerBottom.setOpaque(false);
        centerBottom.add(browseBtn);
        center.add(centerBottom, BorderLayout.SOUTH);
        panel.add(center, BorderLayout.CENTER);

        // 底：主密码 + 解锁
        JPanel bottom = new JPanel(new GridBagLayout());
        bottom.setOpaque(false);
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
            frame.setTitle("flora-sanctum(" + root.getFileName() + ")");
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
        frame.setTitle("flora-sanctum");
        frame.setContentPane(buildUnlockPanel());
        frame.setSize(520, 400);
        frame.revalidate();
    }

    private void switchVault() {
        lock();
    }

    // ================= 主界面 =================

    private JPanel buildMainPanel() {
        JPanel root = new PaperPanel(new BorderLayout());

        // 顶部工具栏（SVG 图标按钮 + tooltip，去文字标签；尺寸 24→29 ≈ +20%）
        newEntryBtn = iconButton(SvgIcon.get("new-entry", 29), "新建条目");
        newGroupBtn = iconButton(SvgIcon.get("new-group", 29), "新建文件夹");
        delBtn = iconButton(SvgIcon.get("delete", 29), "删除");
        syncBtn = iconButton(SvgIcon.get("sync", 29), "同步");
        settingsBtn = iconButton(SvgIcon.get("settings", 29), "设置");
        switchBtn = iconButton(SvgIcon.get("switch", 29), "切换库");
        lockBtn = iconButton(SvgIcon.get("lock", 29), "锁定");
        importImageBtn = iconButton(SvgIcon.get("import-image", 29), "导入图片");
        addSshBtn = iconButton(SvgIcon.get("ssh", 29), "添加 SSH 密钥");
        addRemoteBtn = iconButton(SvgIcon.get("remote", 29), "添加远程");
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
        top.setOpaque(false);
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
        // 按钮栏下方加 1px 细线（与竖线同色）
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(top, BorderLayout.NORTH);
        JPanel hLine = new JPanel();
        hLine.setOpaque(true);
        hLine.setBackground(new java.awt.Color(0xD8, 0xD2, 0xC0));
        hLine.setPreferredSize(new Dimension(0, 1));
        hLine.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        header.add(hLine, BorderLayout.SOUTH);
        root.add(header, BorderLayout.NORTH);

        // 左：组树（"全部"根隐藏，四区段为顶层）
        groupTree = new JTree();
        groupTree.setRootVisible(false);
        groupTree.setFont(groupTree.getFont().deriveFont(Font.PLAIN, 14f));
        groupTree.setRowHeight(36);
        groupTree.setCellRenderer(new FolderTreeRenderer());
        rebuildGroupTree();
        groupTree.addTreeSelectionListener(e -> {
            resetAutoLock();
            updateToolbar();
            refreshEntryList(searchField.getText());
            // 选中文件夹 → 右侧编辑面板显示文件夹编辑
            Object sel = currentSelection();
            if (sel instanceof UUID u && "group".equals(typeOf(u))) {
                renderGroupPanel(u);
            } else {
                clearEditPanel();
            }
        });
        JScrollPane treeScroll = new JScrollPane(groupTree);
        treeScroll.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        treeScroll.setOpaque(false);
        treeScroll.getViewport().setOpaque(false);
        groupTree.setOpaque(false);
        groupTree.setBorder(new javax.swing.border.EmptyBorder(8, 10, 8, 10));

        // 中：条目列表（子文件夹 + 条目混合）
        entryModel = new DefaultListModel<>();
        entryList = new JList<>(entryModel);
        entryList.setFont(entryList.getFont().deriveFont(Font.PLAIN, 14f));
        entryList.setFixedCellHeight(36);
        entryList.setCellRenderer(new EntryListRenderer());
        entryList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                resetAutoLock();
                showSelectedEntry();
            }
        });
        entryList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                // 双击子文件夹 → 进入该文件夹（树联动选中）
                if (e.getClickCount() == 2) {
                    int idx = entryList.locationToIndex(e.getPoint());
                    if (idx >= 0 && idx < listItemTypes.size() && "group".equals(listItemTypes.get(idx))) {
                        navigateToGroup(entryUuids.get(idx));
                    }
                }
            }
        });
        JScrollPane entryScroll = new JScrollPane(entryList);
        entryScroll.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        entryScroll.setOpaque(false);
        entryScroll.getViewport().setOpaque(false);
        entryList.setOpaque(false);
        entryList.setBorder(new javax.swing.border.EmptyBorder(8, 10, 8, 10));

        // 右：编辑面板（无容器边框，区域间只保留 JSplitPane 一条分界线）
        editPanel = new JPanel();
        editPanel.setLayout(new BoxLayout(editPanel, BoxLayout.Y_AXIS));
        editPanel.setBorder(new javax.swing.border.EmptyBorder(8, 10, 8, 10));
        editPanel.setOpaque(false);
        JScrollPane editScroll = new JScrollPane(editPanel);
        editScroll.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        editScroll.setOpaque(false);
        editScroll.getViewport().setOpaque(false);

        JSplitPane rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, entryScroll, editScroll);
        rightSplit.setDividerLocation(280);
        keepDividerRatio(rightSplit, 280);
        rightSplit.setOpaque(false);
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScroll, rightSplit);
        mainSplit.setDividerLocation(220);
        keepDividerRatio(mainSplit, 220);
        mainSplit.setOpaque(false);
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
        RootTag section = sectionOf(currentSelection());
        boolean iconSec = RootTag.ICON == section;
        boolean sshSec = RootTag.SSH_KEY == section;
        boolean remoteSec = RootTag.REMOTE == section;
        importImageBtn.setVisible(iconSec);
        addSshBtn.setVisible(sshSec);
        addRemoteBtn.setVisible(remoteSec);
        // 新建条目/文件夹：仅密码库文件夹上下文可用
        boolean objectsCtx = section == null && currentGroupId() != null; // 选中了普通文件夹
        boolean objectsRoot = RootTag.DATA == section; // 密码库根（可建文件夹）
        newEntryBtn.setEnabled(objectsCtx);
        newGroupBtn.setEnabled(objectsCtx || objectsRoot);
        delBtn.setEnabled(true);
    }

    private boolean isFullyManaged() {
        return sanctum != null
                && new com.flora.sanctum.app.sync.SyncService(sanctum.root()).isFullyManaged();
    }

    // ---- 组树 ----

    /** 树节点类型：普通文件夹（UUID userObject）或区段节点（RootTag userObject，对应根概念）。 */

    private void rebuildGroupTree() {
        treeRoot = new DefaultMutableTreeNode("全部");
        groupNodes.clear();
        groupCache = null; // 重置缓存

        // 四个区段节点（对应根概念，见 RootTag）
        DefaultMutableTreeNode objectsNode = new DefaultMutableTreeNode("密码库");
        objectsNode.setUserObject(RootTag.DATA);
        treeRoot.add(objectsNode);
        // objects 层级：顶层文件夹（ObjectTree 根组，已排除 root group）+ 递归子文件夹
        for (GroupNode g : sanctum.objectTree().rootGroups()) {
            addGroupNode(objectsNode, g.uuid(), g.name());
        }

        DefaultMutableTreeNode iconNode = new DefaultMutableTreeNode("图标");
        iconNode.setUserObject(RootTag.ICON);
        treeRoot.add(iconNode);

        DefaultMutableTreeNode sshNode = new DefaultMutableTreeNode("SSH 密钥");
        sshNode.setUserObject(RootTag.SSH_KEY);
        treeRoot.add(sshNode);

        DefaultMutableTreeNode remoteNode = new DefaultMutableTreeNode("远程");
        remoteNode.setUserObject(RootTag.REMOTE);
        treeRoot.add(remoteNode);

        groupTree.setModel(new DefaultTreeModel(treeRoot));
        // 根隐藏，四区段展开
        for (int r = 0; r < groupTree.getRowCount(); r++) {
            groupTree.expandRow(r);
        }
    }

    private void addGroupNode(DefaultMutableTreeNode parentNode, UUID id, String name) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(
                name == null || name.isBlank() ? "未命名" : name);
        node.setUserObject(id);
        parentNode.add(node);
        groupNodes.put(id, node);
        GroupNode g = sanctum.objectTree().group(id);
        if (g != null) {
            for (GroupNode child : g.childGroups()) {
                addGroupNode(node, child.uuid(), child.name());
            }
        }
    }

    private Map<UUID, String[]> groupCache;

    /** group → {parent, name}（来自 ObjectTree）。 */
    private Map<UUID, String[]> groupsById() {
        if (groupCache == null) {
            groupCache = new LinkedHashMap<>();
            for (TreeNode n : sanctum.objectTree().nodes()) {
                if (n instanceof GroupNode g) {
                    groupCache.put(g.uuid(), new String[]{g.parent(), g.name()});
                }
            }
        }
        return groupCache;
    }

    // ---- 条目列表 ----

    private void refreshEntryList(String filter) {
        entryModel.clear();
        entryUuids.clear();
        listItemTypes.clear();
        String q = filter == null ? "" : filter.trim().toLowerCase();
        Object sel = currentSelection();
        RootTag section = sectionOf(sel);
        UUID groupId = section == null ? groupIdOf(sel) : null;

        if (RootTag.ICON == section) {
            for (IconNode icon : sanctum.iconTree().icons()) {
                entryModel.addElement(iconLabel(icon));
                entryUuids.add(icon.uuid());
                listItemTypes.add("icon");
            }
            return;
        }
        if (RootTag.SSH_KEY == section) {
            for (SshKeyNode key : sanctum.sshKeyTree().keys()) {
                entryModel.addElement(key.name());
                entryUuids.add(key.uuid());
                listItemTypes.add("sshKey");
            }
            return;
        }
        if (RootTag.REMOTE == section) {
            for (RemoteNode r : sanctum.remoteTree().remotes()) {
                entryModel.addElement(r.name());
                entryUuids.add(r.uuid());
                listItemTypes.add("field");
            }
            return;
        }

        // 密码库层级：当前文件夹的子文件夹 + 条目（groupId=null 为顶层）
        GroupNode currentGroup = groupId == null ? null : sanctum.objectTree().group(groupId);
        List<? extends TreeNode> items = currentGroup != null
                ? currentGroup.children()
                : java.util.stream.Stream.concat(
                        sanctum.objectTree().rootGroups().stream(),
                        sanctum.objectTree().rootEntries().stream()).toList();
        for (TreeNode n : items) {
            if (n instanceof GroupNode g) {
                String gname = g.name();
                if (gname == null || gname.isBlank()) {
                    gname = "未命名";
                }
                if (!q.isEmpty() && !gname.toLowerCase().contains(q)) {
                    continue;
                }
                entryModel.addElement(gname);
                entryUuids.add(g.uuid());
                listItemTypes.add("group");
            } else if (n instanceof EntryNode e) {
                if (!q.isEmpty() && !matchesFilter(e, q)) {
                    continue;
                }
                entryModel.addElement(e.name());
                entryUuids.add(e.uuid());
                listItemTypes.add("entry");
            }
        }
    }

    /** 图标区段条目显示名（含格式/尺寸提示）。 */
    private String iconLabel(IconNode icon) {
        String format = icon.format();
        return (format == null ? "图标" : "图标 [" + format + "]");
    }

    private boolean matchesFilter(EntryNode entry, String q) {
        if (entry.name() != null && entry.name().toLowerCase().contains(q)) {
            return true;
        }
        for (FieldNode f : entry.fields()) {
            if (f.fieldName() != null && f.fieldName().toLowerCase().contains(q)) {
                return true;
            }
            if (f.value() != null && f.value().toLowerCase().contains(q)) {
                return true;
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

    /** 若当前选中是区段节点（RootTag userObject）则返回，否则 null。 */
    private RootTag sectionOf(Object sel) {
        return sel instanceof RootTag t ? t : null;
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
        if ("group".equals(type)) {
            renderGroupPanel(u);
        } else if ("icon".equals(type)) {
            renderIconPanel(u);
        } else if ("sshKey".equals(type)) {
            renderSshKeyPanel(u);
        } else if ("remote".equals(type) || "field".equals(type)) {
            renderRemotePanel(u);
        } else {
            renderEntry(selectedEntry);
        }
    }

    /** 双击列表中的子文件夹：树联动选中并刷新（进入该文件夹）。 */
    private void navigateToGroup(UUID groupUuid) {
        DefaultMutableTreeNode node = groupNodes.get(groupUuid);
        if (node != null) {
            groupTree.setSelectionPath(new javax.swing.tree.TreePath(node.getPath()));
        }
    }

    /** 图标详情面板：预览图片 + 提示。 */
    private void renderIconPanel(UUID iconUuid) {
        editPanel.removeAll();
        IconNode icon = sanctum.iconTree().find(iconUuid);
        addInfoLabel("图标 [格式 " + (icon == null ? "?" : icon.format()) + "]");
        addInfoLabel("自定义图标，可在条目编辑中选择使用");
        editPanel.revalidate();
        editPanel.repaint();
    }

    /** SSH 密钥详情面板。 */
    private void renderSshKeyPanel(UUID keyUuid) {
        editPanel.removeAll();
        SshKeyNode key = sanctum.sshKeyTree().find(keyUuid);
        addInfoLabel("SSH 密钥：" + (key == null ? "?" : key.name()));
        addInfoLabel("私钥已加密存储");
        editPanel.revalidate();
        editPanel.repaint();
    }

    /** 远程配置详情面板。 */
    private void renderRemotePanel(UUID remoteUuid) {
        editPanel.removeAll();
        RemoteNode remote = sanctum.remoteTree().find(remoteUuid);
        addInfoLabel("远程：" + (remote == null ? "?" : remote.name()));
        editPanel.revalidate();
        editPanel.repaint();
    }

    /** 添加一行受 max height 约束的只读信息标签。 */
    private void addInfoLabel(String text) {
        JLabel label = new JLabel(text);
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        editPanel.add(label);
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

    /** 文件夹编辑面板：名称 + 保存（改名）+ 删除。 */
    private void renderGroupPanel(UUID groupUuid) {
        editPanel.removeAll();
        GroupNode group = sanctum.objectTree().group(groupUuid);
        if (group == null) {
            return;
        }
        JPanel nameRow = new JPanel(new BorderLayout(6, 0));
        nameRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        JLabel nameTag = new JLabel("名称*");
        nameTag.setFont(nameTag.getFont().deriveFont(Font.BOLD, 14f));
        nameTag.setForeground(new java.awt.Color(180, 60, 60));
        JTextField nameField = new JTextField(group.name() == null ? "" : group.name());
        nameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        nameRow.add(nameTag, BorderLayout.WEST);
        nameRow.add(nameField, BorderLayout.CENTER);
        editPanel.add(nameRow);

        JButton saveBtn = new JButton("保存");
        saveBtn.addActionListener(e -> {
            String newName = nameField.getText().trim();
            if (newName.isEmpty()) {
                statusLabel.setText("文件夹名称不能为空");
                return;
            }
            resetAutoLock();
            try {
                group.rename(newName);
                rebuildGroupTree();
                refreshEntryList(currentSearchQuery());
                statusLabel.setText("已保存");
            } catch (Exception ex) {
                statusLabel.setText("保存失败");
            }
        });
        JButton delGroupBtn = new JButton("删除文件夹");
        delGroupBtn.addActionListener(e -> {
            int ok = JOptionPane.showConfirmDialog(frame, "删除该文件夹及其内容?", "确认", JOptionPane.YES_NO_OPTION);
            if (ok == JOptionPane.YES_OPTION) {
                resetAutoLock();
                deleteGroupRecursive(groupUuid);
                rebuildGroupTree();
                refreshEntryList(currentSearchQuery());
                clearEditPanel();
            }
        });
        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        actionRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        actionRow.add(saveBtn);
        actionRow.add(delGroupBtn);
        editPanel.add(actionRow);
        editPanel.revalidate();
        editPanel.repaint();
    }

    /** 渲染条目编辑面板（内置字段 + 自定义字段）。 */
    private void renderEntry(UUID entryUuid) {
        editPanel.removeAll();
        EntryNode entryNode = sanctum.objectTree().entry(entryUuid);
        if (entryNode == null) {
            return;
        }
        // 名称
        JTextField nameField = makeEntryField(entryNode.name());
        editPanel.add(makeEntryRow("名称 :", nameField, false));

        // 内置预设字段：url / username / password / labels（无 kind 下拉）
        JTextField urlField = makeEntryField(entryNode.url());
        editPanel.add(makeEntryRow("URL :", urlField, false));
        JTextField usernameField = makeEntryField(entryNode.username());
        editPanel.add(makeEntryRow("用户名 :", usernameField, false));
        JTextField passwordField = makeEntryField(entryNode.password());
        editPanel.add(makeEntryRow("密码 :", passwordField, false));
        JTextField labelsField = makeEntryField(
                com.flora.sanctum.model.EntryFields.labelsToString(entryNode.labels()));
        editPanel.add(makeEntryRow("标签 :", labelsField, false));

        // 时间信息（只读）
        editPanel.add(makeInfoRow("创建时间", formatTime(entryNode.createTime())));
        editPanel.add(makeInfoRow("更新时间", formatTime(entryNode.updateTime())));

        // 自定义字段区
        Map<UUID, JTextField> fieldInputs = new LinkedHashMap<>();
        Map<UUID, JComboBox<String>> kindInputs = new LinkedHashMap<>();
        for (FieldNode field : entryNode.fields()) {
            String fn = field.fieldName();
            String val = field.value();
            String kind = field.kind();
            JPanel row = new JPanel(new BorderLayout(6, 0));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
            JLabel fLabel = new JLabel((fn == null ? "" : fn) + " :");
            fLabel.setPreferredSize(new Dimension(110, 24));
            JTextField fValue = makeEntryField(val);
            row.add(fLabel, BorderLayout.WEST);
            row.add(fValue, BorderLayout.CENTER);
            // EAST：kind 下拉 + 删除按钮
            JPanel east = new JPanel();
            east.setLayout(new BoxLayout(east, BoxLayout.Y_AXIS));
            east.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
            JComboBox<String> kindCombo = new JComboBox<>(kindOptions().toArray(new String[0]));
            String curKind = kind == null ? "text" : kind;
            if (!containsItem(kindCombo, curKind)) {
                kindCombo.addItem(curKind);
            }
            kindCombo.setSelectedItem(curKind);
            kindCombo.setMaximumSize(new Dimension(120, 24));
            kindCombo.setPreferredSize(new Dimension(120, 24));
            east.add(kindCombo);
            JButton delField = new JButton("×");
            delField.setToolTipText("删除字段 " + fn);
            delField.setMargin(new Insets(0, 0, 0, 0));
            delField.setPreferredSize(new Dimension(24, 24));
            delField.addActionListener(e -> {
                resetAutoLock();
                try {
                    field.delete();
                    renderEntry(entryUuid);
                    statusLabel.setText("字段已删除");
                } catch (Exception ex) {
                    statusLabel.setText("删除失败");
                }
            });
            east.add(delField);
            row.add(east, BorderLayout.EAST);
            // TOTP 字段显示验证码
            if ("totp".equals(kind)) {
                try {
                    JLabel totp = new JLabel("  验证码: " + field.totpCode());
                    row.add(totp, BorderLayout.SOUTH);
                } catch (Exception ignore) {
                }
            }
            editPanel.add(row);
            fieldInputs.put(field.uuid(), fValue);
            kindInputs.put(field.uuid(), kindCombo);
        }

        // 操作行：保存 / 添加字段 / 复制密码 / 选择图标
        JButton saveBtn = new JButton("保存");
        saveBtn.addActionListener(e -> {
            String newName = nameField.getText().trim();
            if (newName.isEmpty()) {
                newName = "未命名";
            }
            resetAutoLock();
            try {
                if (!newName.equals(entryNode.name())) {
                    entryNode.rename(newName);
                }
                entryNode.updateBuiltins(new com.flora.sanctum.model.EntryFields(
                        passwordField.getText(),
                        urlField.getText(),
                        usernameField.getText(),
                        com.flora.sanctum.model.EntryFields.parseLabels(labelsField.getText())));
            } catch (Exception ex) {
                statusLabel.setText("保存失败");
                return;
            }
            saveFieldInputs(fieldInputs, kindInputs, entryUuid);
            renderEntry(entryUuid);
            statusLabel.setText("已保存");
        });
        JButton addFieldBtn = new JButton("+ 添加字段");
        addFieldBtn.addActionListener(e -> addFieldDialog(entryUuid));
        JButton copyBtn = new JButton("复制密码");
        copyBtn.addActionListener(e -> copyPassword(entryUuid));
        JButton iconBtn = new JButton("选择图标");
        iconBtn.addActionListener(e -> chooseEntryIcon(entryUuid));
        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        actionRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        actionRow.add(saveBtn);
        actionRow.add(addFieldBtn);
        actionRow.add(copyBtn);
        actionRow.add(iconBtn);
        editPanel.add(actionRow);

        editPanel.revalidate();
        editPanel.repaint();
    }

    /** 生成单行文本字段（最大高度 24，避免 BoxLayout 拉伸）。 */
    private JTextField makeEntryField(String value) {
        JTextField f = new JTextField(value == null ? "" : value);
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        return f;
    }

    /** 工具栏图标按钮（纯 SVG 图标 + tooltip，去边框/底色）。 */
    private static JButton iconButton(Icon icon, String tip) {
        JButton b = new JButton(icon);
        b.setToolTipText(tip);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setMargin(new Insets(3, 10, 3, 10));
        return b;
    }

    /** JSplitPane 分隔线记忆百分比：resize 时按初始比例重设，避免全屏/窗口切换后位置失衡。 */
    private static void keepDividerRatio(JSplitPane split, int initialDivider) {
        split.addComponentListener(new java.awt.event.ComponentAdapter() {
            private double ratio = -1;

            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                int w = split.getWidth();
                if (w <= 0) {
                    return;
                }
                if (ratio < 0) {
                    ratio = initialDivider / (double) w;
                } else {
                    split.setDividerLocation((int) Math.round(ratio * w));
                }
            }
        });
    }

    /** 构造一行：左标签 + 右文本字段（高度受限）。required=true 时标签标红加粗。 */
    private JPanel makeEntryRow(String label, JTextField field, boolean required) {
        JPanel row = new JPanel(new BorderLayout(0, 0));
        javax.swing.border.EmptyBorder pad = new javax.swing.border.EmptyBorder(0, 0, 8, 0);
        row.setBorder(pad);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        JLabel tag = new JLabel(label);
        tag.setPreferredSize(new Dimension(110, 24));
        if (required) {
            tag.setForeground(new java.awt.Color(180, 60, 60));
            tag.setFont(tag.getFont().deriveFont(Font.BOLD));
        }
        row.add(tag, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        return row;
    }

    /** 只读信息行（创建时间/更新时间）。 */
    private JPanel makeInfoRow(String label, String text) {
        JPanel row = new JPanel(new BorderLayout(0, 0));
        row.setBorder(new javax.swing.border.EmptyBorder(0, 0, 8, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        JLabel tag = new JLabel(label);
        tag.setPreferredSize(new Dimension(110, 24));
        tag.setFont(tag.getFont().deriveFont(Font.ITALIC));
        JLabel value = new JLabel(text == null ? "" : text);
        value.setForeground(java.awt.Color.GRAY);
        row.add(tag, BorderLayout.WEST);
        row.add(value, BorderLayout.CENTER);
        return row;
    }

    private JLabel makeLegend(String text) {
        JLabel legend = new JLabel(text);
        legend.setFont(legend.getFont().deriveFont(Font.ITALIC, 10f));
        legend.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        return legend;
    }

    /** 把本地时间毫秒格式化为可读字符串（null → ""）。 */
    private static String formatTime(Long ts) {
        if (ts == null || ts == 0) {
            return "";
        }
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                .format(new java.util.Date(ts));
    }

    private void addFieldDialog(UUID entryUuid) {
        JTextField nameField = new JTextField(12);
        JTextField valField = new JTextField(18);
        JComboBox<String> kindCombo = new JComboBox<>(kindOptions().toArray(new String[0]));
        kindCombo.setSelectedItem("text");
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 6));
        form.add(new JLabel("字段名:"));
        form.add(nameField);
        form.add(new JLabel("值:"));
        form.add(valField);
        form.add(new JLabel("类型:"));
        form.add(kindCombo);
        int ok = JOptionPane.showConfirmDialog(frame, form, "添加字段", JOptionPane.OK_CANCEL_OPTION);
        if (ok == JOptionPane.OK_OPTION) {
            String fn = nameField.getText().trim();
            if (fn.isEmpty()) {
                statusLabel.setText("字段名不能为空");
                return;
            }
            resetAutoLock();
            try {
                EntryNode entryNode = sanctum.objectTree().entry(entryUuid);
                if (entryNode == null) {
                    statusLabel.setText("条目不存在");
                    return;
                }
                entryNode.createField(fn, valField.getText(), (String) kindCombo.getSelectedItem());
                renderEntry(entryUuid);
                statusLabel.setText("字段已添加");
            } catch (Exception ex) {
                statusLabel.setText("添加失败");
            }
        }
    }

    /** 由条目推导其所属组 uuid（顶层条目 parent 为根概念 tag 返回 null）。 */
    private UUID groupIdOf(UUID entryUuid) {
        EntryNode entry = sanctum.objectTree().entry(entryUuid);
        if (entry == null) {
            return null;
        }
        String p = entry.parent();
        return p == null || RootTag.isRoot(p) ? null : UUID.fromString(p);
    }

    /** kind 下拉选项：预定义 FieldKind tag + 库内未预定义的 kind（向后兼容，未知 kind 可继续选择）。 */
    private List<String> kindOptions() {
        List<String> out = new ArrayList<>();
        for (FieldKind k : FieldKind.values()) {
            out.add(k.tag());
        }
        for (DataTree t : sanctum.trees()) {
            for (TreeNode n : t.nodes()) {
                if (n instanceof FieldNode f) {
                    String kind = f.kind();
                    if (kind != null && FieldKind.fromTag(kind) == null && !out.contains(kind)) {
                        out.add(kind);
                    }
                }
            }
        }
        return out;
    }

    private static boolean containsItem(JComboBox<?> combo, Object item) {
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (java.util.Objects.equals(combo.getItemAt(i), item)) {
                return true;
            }
        }
        return false;
    }

    /** 保存按钮：逐个提交所有字段的值与 kind，任一失败则记录并提示。 */
    private void saveFieldInputs(Map<UUID, JTextField> inputs, Map<UUID, JComboBox<String>> kindInputs,
                                 UUID entryUuid) {
        resetAutoLock();
        int failed = 0;
        for (Map.Entry<UUID, JTextField> e : inputs.entrySet()) {
            try {
                FieldNode field = sanctum.objectTree().field(e.getKey());
                if (field != null) {
                    field.updateValue(e.getValue().getText());
                }
            } catch (Exception ex) {
                failed++;
            }
        }
        for (Map.Entry<UUID, JComboBox<String>> e : kindInputs.entrySet()) {
            try {
                FieldNode field = sanctum.objectTree().field(e.getKey());
                if (field == null) {
                    continue;
                }
                String oldKind = field.kind();
                String oldNorm = oldKind == null ? "text" : oldKind;
                String selected = (String) e.getValue().getSelectedItem();
                if (!oldNorm.equals(selected)) {
                    field.updateKind(selected);
                }
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
        EntryNode entry = sanctum.objectTree().entry(entryUuid);
        String val = entry == null ? null : entry.password();
        if (val == null) {
            statusLabel.setText("未设置密码");
            return;
        }
        setClipboard(val);
        copiedPlaintext = val;
        startClipboardTimer();
        statusLabel.setText("已复制");
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
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
                sanctum.iconTree().createIcon(data, "svg");
            } else {
                javax.imageio.ImageIO.read(file.toFile()); // 校验确为可读图片
                sanctum.iconTree().createIcon(data, format);
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
            sanctum.sshKeyTree().createSshKey(name, pem);
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
            sanctum.remoteTree().addRemote(name, url, keyRef.isEmpty() ? null : keyRef);
            refreshEntryList(currentSearchQuery());
            statusLabel.setText("已添加远程 " + name);
        } catch (Exception ex) {
            statusLabel.setText("远程添加失败");
        }
    }

    /** 为条目选择一个已导入的自定义图标（或清除图标）。 */
    private void chooseEntryIcon(UUID entryUuid) {
        List<IconNode> icons = sanctum.iconTree().icons();
        if (icons.isEmpty()) {
            statusLabel.setText("请先在\"图标\"区导入图片");
            return;
        }
        String[] choices = icons.stream()
                .map(icon -> iconLabel(icon) + "  [" + icon.uuid() + "]")
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
            EntryNode entry = sanctum.objectTree().entry(entryUuid);
            if (entry != null) {
                entry.setIcon(UUID.fromString(uuidStr));
            }
            renderEntry(entryUuid);
            statusLabel.setText("已设置图标");
        } catch (Exception ex) {
            statusLabel.setText("设置图标失败");
        }
    }

    // ================= 新建 / 删除 =================

    /**
     * 新建条目：不弹录入对话框，直接以空白名创建（含空密码字段），
     * 选中并在编辑面板打开，让用户改名/填密码。
     */
    private void doNewEntry() {
        UUID groupId = currentGroupId();
        if (groupId == null) {
            statusLabel.setText("请先在密码库中选中一个文件夹");
            return;
        }
        UUID entryUuid = sanctum.objectTree().createEntry(groupId, "新建条目",
                new com.flora.sanctum.model.EntryFields("", null, null, java.util.List.of())).uuid();
        resetAutoLock();
        rebuildGroupTree();
        // 恢复树选中当前文件夹
        DefaultMutableTreeNode groupNode = groupNodes.get(groupId);
        if (groupNode != null) {
            groupTree.setSelectionPath(new javax.swing.tree.TreePath(groupNode.getPath()));
        }
        refreshEntryList(currentSearchQuery());
        selectInList(entryUuid);
        showSelectedEntry();
        statusLabel.setText("已新建条目，请填写名称与密码");
    }

    /** 在条目列表中选中指定对象（若在列表中）。 */
    private void selectInList(UUID uuid) {
        int idx = entryUuids.indexOf(uuid);
        if (idx >= 0) {
            entryList.setSelectedIndex(idx);
        }
    }

    /** 新建文件夹：不弹对话框，直接以空白名创建，树选中并打开文件夹编辑。 */
    private void doNewGroup() {
        Object sel = currentSelection();
        // 仅在密码库根（RootTag.DATA 区段）或普通文件夹下允许新建文件夹
        RootTag section = sectionOf(sel);
        if (RootTag.ICON == section || RootTag.SSH_KEY == section || RootTag.REMOTE == section) {
            statusLabel.setText("该区段不允许新建文件夹");
            return;
        }
        UUID parentId = groupIdOf(sel);
        UUID groupUuid = sanctum.objectTree().createGroup(parentId, "新建文件夹").uuid();
        rebuildGroupTree();
        resetAutoLock();
        DefaultMutableTreeNode node = groupNodes.get(groupUuid);
        if (node != null) {
            groupTree.setSelectionPath(new javax.swing.tree.TreePath(node.getPath()));
        }
        refreshEntryList(currentSearchQuery());
        renderGroupPanel(groupUuid);
        statusLabel.setText("已新建文件夹，请重命名");
    }

    private void doDelete() {
        Object sel = currentSelection();
        RootTag section = sectionOf(sel);
        UUID entryUuid = selectedEntryUuid();
        if (entryUuid != null) {
            String type = typeOf(entryUuid);
            String what = switch (type == null ? "" : type) {
                case "group" -> "该文件夹";
                case "icon" -> "该图标";
                case "sshKey" -> "该 SSH 密钥";
                case "remote" -> "该远程配置";
                default -> "该条目";
            };
            int ok = JOptionPane.showConfirmDialog(frame, "删除" + what + "?", "确认", JOptionPane.YES_NO_OPTION);
            if (ok == JOptionPane.YES_OPTION) {
                resetAutoLock();
                TreeNode node = sanctum.findNode(entryUuid);
                if (node != null) {
                    node.delete();
                }
                refreshEntryList(currentSearchQuery());
                rebuildGroupTree();
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

    /** 对象类型（group/entry/field/icon/sshKey），未知返回 null。 */
    private String typeOf(UUID uuid) {
        TreeNode n = sanctum.findNode(uuid);
        return n == null ? null : n.type();
    }

    private void deleteGroupRecursive(UUID groupId) {
        GroupNode g = sanctum.objectTree().group(groupId);
        if (g != null) {
            g.delete(); // GroupNode.delete 已递归
        }
    }

    // ================= 同步 =================

    private void doSync() {
        resetAutoLock();
        if (sanctum == null) {
            return;
        }
        try {
            com.flora.sanctum.app.sync.SyncService sync = new com.flora.sanctum.app.sync.SyncService(sanctum.root());
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

    /** 纸感底纹背景面板：确定性平滑值噪声按面板尺寸整体渲染（函数全局连续，平铺无接缝）。 */
    private static final class PaperPanel extends JPanel {
        private BufferedImage cached;
        private int cachedW = -1;
        private int cachedH = -1;

        PaperPanel(LayoutManager layout) {
            super(layout);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            if (cached == null || cachedW != w || cachedH != h) {
                cached = renderPaper(w, h);
                cachedW = w;
                cachedH = h;
            }
            g.drawImage(cached, 0, 0, null);
        }

        /** 按面板尺寸渲染暖白纸纤维噪声图（复用 flora-root PaperNoise，基色 #F8F4E9，幅度 ±25）。 */
        private static BufferedImage renderPaper(int w, int h) {
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            int[] px = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    float n = com.flora.root.graphics.noise.PaperNoise.paper(x, y);
                    int d = (int) Math.round(n * 25);
                    int r = com.flora.root.graphics.noise.PaperNoise.clamp(0xF8 + d);
                    int g = com.flora.root.graphics.noise.PaperNoise.clamp(0xF4 + d);
                    int b = com.flora.root.graphics.noise.PaperNoise.clamp(0xE9 + d);
                    px[y * w + x] = (r << 16) | (g << 8) | b;
                }
            }
            img.setRGB(0, 0, w, h, px, 0, w);
            return img;
        }
    }

    /** 组树渲染器：按 userObject 类型渲染文本（RootTag→区段展示名；UUID→group name；其它 fallback）。 */
    private final class FolderTreeRenderer extends javax.swing.tree.DefaultTreeCellRenderer {
        @Override
        public java.awt.Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                               boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            setIcon(SvgIcon.get("folder", 24));
            setDisabledIcon(SvgIcon.get("folder", 24));
            if (value instanceof javax.swing.tree.DefaultMutableTreeNode node) {
                Object uo = node.getUserObject();
                if (uo instanceof RootTag tag) {
                    setText(tag.displayName());
                } else if (uo instanceof UUID uuid) {
                    String[] info = groupsById().get(uuid);
                    String name = info == null ? null : info[1];
                    setText(name == null || name.isBlank() ? "未命名" : name);
                }
            }
            return this;
        }
    }

    /** 条目列表渲染器：文件夹图标 + 锁图标，按列表项类型区分。 */
    private final class EntryListRenderer extends javax.swing.DefaultListCellRenderer {
        @Override
        public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                               boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            String type = index >= 0 && index < listItemTypes.size() ? listItemTypes.get(index) : null;
            setIcon("group".equals(type) ? SvgIcon.get("folder", 24) : SvgIcon.get("entry", 24));
            return this;
        }
    }
}
