package com.flora.sanctum.app.ui;

import com.flora.sanctum.config.UserConfig;
import com.flora.sanctum.model.StoredNodeType;
import com.flora.sanctum.model.ViewNodeType;
import com.flora.sanctum.model.tree.DataTree;
import com.flora.sanctum.model.tree.EntryNode;
import com.flora.sanctum.model.FieldKind;
import com.flora.sanctum.model.tree.FieldNode;
import com.flora.sanctum.model.tree.GroupNode;
import com.flora.sanctum.model.tree.IconNode;
import com.flora.sanctum.model.tree.RemoteNode;
import com.flora.sanctum.model.Sanctum;
import com.flora.sanctum.model.tree.SshKeyNode;
import com.flora.sanctum.model.tree.TreeNode;
import com.flora.root.codec.json.model.JsonObject;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
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
 * 纯 JDK（Swing/AWT，java.desktop）。四页：历史仓库列表（应用形态首页，含新建/导入/打开）、
 * 解锁页（针对特定仓库；应用形态锁定后直接回到该仓库解锁页并带"回到历史列表"）、
 * 三栏编辑页（组树/条目列表/编辑面板）、仓库设置页。支持新建/删除条目与文件夹、字段增删与编辑、
 * 条目重命名、搜索、TOTP、复制、同步、设置、锁定；独立形态无历史列表、不提供切换库。
 * UI 只调用 core 公开 API（见设计 07），不解密、不碰 Git。
 */
public final class SanctumGui {

    private final java.util.concurrent.atomic.AtomicReference<Sanctum> current =
            new java.util.concurrent.atomic.AtomicReference<>();
    private final UserConfig config;
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
    /** 与 entryUuids 平行的列表项存储类型（group/entry/icon/sshKey/field），供图标与双击导航。 */
    private final List<StoredNodeType> listItemTypes = new ArrayList<>();
    /** 与 listItemTypes 平行的条目图标 id（"builtin:name" 或 uuid；无则 null），列表渲染优先用。 */
    private final List<String> listItemIcons = new ArrayList<>();
    private JPanel editPanel;
    private JLabel statusLabel;
    private UUID selectedEntry;
    private String copiedPlaintext;
    private java.util.Timer autoLockTimer;
    private java.util.Timer clipboardTimer;
    private String unlockedVaultPath;
    /** 独立仓库形态（standalone.json 判定）：无历史仓库列表页。 */
    private boolean standalone;
    /** 当前仓库数据根（解锁目标 / 锁定后直接回到该仓库解锁页）。 */
    private Path targetVaultRoot;
    /** 当前解锁页是否对应"新建"（true）还是"打开"（false）：新建走 createAndUnlock，打开只 open+unlock 不自动新建。 */
    private boolean unlockIsCreate;
    /** 垃圾桶视图（每次重建树时刷新；含三类异常节点 uuid 与「原位置」计算）。 */
    private com.flora.sanctum.model.TrashView trashView;

    /** 垃圾桶区段下的三类子分组标记（作为树节点 userObject）。 */
    private enum TrashCategory {
        MANUAL("手动删除", com.flora.sanctum.model.TrashView.TrashKind.MANUAL),
        UNREACHABLE("不可达", com.flora.sanctum.model.TrashView.TrashKind.UNREACHABLE),
        UNLOCKABLE("不可解锁", com.flora.sanctum.model.TrashView.TrashKind.UNLOCKABLE);

        final String label;
        final com.flora.sanctum.model.TrashView.TrashKind kind;

        TrashCategory(String label, com.flora.sanctum.model.TrashView.TrashKind kind) {
            this.label = label;
            this.kind = kind;
        }
    }

    /** 应用形态：读系统级配置（~/.flora-sanctum/config.json），页面从历史仓库列表开始。 */
    private SanctumGui() {
        this.config = new UserConfig();
        this.standalone = false;
    }

    /** 独立仓库形态：读仓库级配置（仓库根 standalone.json），页面从该仓库解锁页开始。 */
    private SanctumGui(Path repoRoot) {
        this.config = new UserConfig(repoRoot);
        this.standalone = true;
    }

    /** 应用形态入口：历史仓库列表页。 */
    public static void launch() {
        new SanctumGui().run();
    }

    /** 独立仓库形态：直接进入指定数据根的解锁页。 */
    public static void launchDirect(Path repoRoot, Path vaultRoot) {
        SanctumGui gui = new SanctumGui(repoRoot);
        gui.targetVaultRoot = vaultRoot;
        gui.run();
    }

    private void run() {
        // 启动时未解锁无法读仓库设置，主题用默认；解锁后按仓库主题应用
        applyTheme(com.flora.sanctum.model.LibraryConfig.DEFAULT_THEME);
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
            // 窗口 resize 即时保存当前页面类别（引导/主页面）的尺寸到全局配置
            frame.addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override
                public void componentResized(java.awt.event.ComponentEvent e) {
                    if (currentWindowKey != null && frame.getWidth() > 0 && frame.getHeight() > 0) {
                        config.setWindowSize(currentWindowKey, frame.getWidth(), frame.getHeight());
                    }
                }
            });
            if (standalone) {
                showUnlockPage(targetVaultRoot);
            } else {
                showHistoryPage();
            }
            frame.setSize(560, 440);
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

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase().contains("mac");
    }

    /** 白灰偏暖黄色调：仿 CodeBuddy 极简白灰，但背景略偏纸黄。统一主题见 {@link UiTheme}。 */
    private void applyPaperTheme() {
        UiTheme.apply();
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

    // ================= 解锁页（针对特定仓库） =================

    // ================= 页面切换 =================

    private void showHistoryPage() {
        frame.setContentPane(buildHistoryPanel());
        applyWindowSize("ui.window.guide", 600, 480);
        frame.revalidate();
    }

    private void showUnlockPage(Path root) {
        targetVaultRoot = root;
        frame.setContentPane(buildUnlockPanel(root));
        applyWindowSize("ui.window.guide", 600, 480);
        frame.revalidate();
    }

    private void showEditPage() {
        frame.setContentPane(buildMainPanel());
        applyWindowSize("ui.window.main", 960, 640);
        frame.revalidate();
    }

    private void showSettingsPage() {
        frame.setContentPane(buildSettingsPanel());
        applyWindowSize("ui.window.main", 960, 640); // 与主界面同尺寸类别
        frame.revalidate();
    }

    /** 应用窗口尺寸：有存储值用之，否则默认；resize 时由 frame 监听即时保存。 */
    private void applyWindowSize(String key, int defaultW, int defaultH) {
        currentWindowKey = key;
        int[] sz = config.windowSize(key);
        frame.setSize(sz != null ? sz[0] : defaultW, sz != null ? sz[1] : defaultH);
    }

    // ================= 历史仓库列表页（应用形态首页，含新建/导入/打开） =================

    private JPanel buildHistoryPanel() {
        JPanel panel = new UiTheme.PaperPanel(new BorderLayout(0, 10));
        panel.setBorder(new EmptyBorder(16, 20, 16, 20));

        // 顶部：左标题，右操作按钮（新建/打开/克隆/设置）
        JLabel title = new JLabel("flora-sanctum");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        JButton newBtn = new JButton("新建仓库");
        newBtn.setToolTipText("建立一个新的密码仓库（普通或独立）");
        newBtn.addActionListener(e -> doNewVault());
        JButton openBtn = new JButton("打开仓库");
        openBtn.setToolTipText("打开已存在的仓库");
        openBtn.addActionListener(e -> doOpenVault());
        JButton cloneBtn = new JButton("克隆仓库");
        cloneBtn.setToolTipText("从远程 git 仓库克隆导入");
        cloneBtn.addActionListener(e -> doImportVault());
        JPanel topBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        topBtns.setOpaque(false);
        topBtns.add(newBtn);
        topBtns.add(openBtn);
        topBtns.add(cloneBtn);
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(title, BorderLayout.WEST);
        top.add(topBtns, BorderLayout.EAST);
        panel.add(top, BorderLayout.NORTH);

        // 中：历史仓库列表（逐行组件：圆形字母图标 + 名称/简化路径 + 打开文件夹/删除）
        JPanel list = new JPanel(new java.awt.GridBagLayout());
        list.setOpaque(false);
        java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0; // 必须显式初始化（默认 RELATIVE(-1) 会使首两条同 gridy 重叠）
        gbc.weightx = 1.0;
        gbc.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gbc.anchor = java.awt.GridBagConstraints.NORTH;
        for (String p : config.recentVaults()) {
            list.add(buildRecentRow(list, p), gbc);
            gbc.gridy++;
        }
        gbc.weighty = 1.0; // 底部留白
        list.add(javax.swing.Box.createVerticalGlue(), gbc);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setOpaque(false);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    /** 单个历史仓库行：圆形字母图标 + 名称/简化路径 + 打开文件夹/删除按钮；双击行打开仓库。 */
    private JPanel buildRecentRow(JPanel list, String path) {
        boolean exists = Files.exists(Path.of(path));
        String name = dirName(path);
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(8, 10, 8, 10));
        // 显式固定行高：嵌套布局的 preferredSize 高度可能被算小导致文字溢出与相邻行叠压，
        // 固定行高让所有组件在行内垂直居中，绝不超过行边界。
        int rowH = 56;
        row.setPreferredSize(new Dimension(0, rowH));
        row.setMinimumSize(new Dimension(0, rowH));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, rowH));

        // 左：圆形字母图标
        row.add(new JLabel(new LetterIcon(name)), BorderLayout.WEST);

        // 中：名称（粗体）+ 简化路径（灰字）。用 GridLayout(2,1) 而非 BoxLayout：
        // GridLayout 强制两行等高 = 容器总高/2，不会按 JLabel preferred 截断高度，
        // 避免 HTML 单 JLabel 换行溢出导致与下方条目重叠。
        JPanel text = new JPanel(new GridLayout(2, 1, 0, 2));
        text.setOpaque(false);
        JLabel nameLabel = new JLabel(name, JLabel.LEFT);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 14f));
        JLabel pathLabel = new JLabel(simplifyPath(path), JLabel.LEFT);
        pathLabel.setFont(pathLabel.getFont().deriveFont(Font.PLAIN, 11f));
        pathLabel.setForeground(UiTheme.INK_MUTED);
        if (!exists) {
            nameLabel.setText("<html><strike>" + name + "</strike> <font color='#999'>（失效）</font></html>");
            pathLabel.setText("<html><strike>" + simplifyPath(path) + "</strike></html>");
        }
        text.add(nameLabel);
        text.add(pathLabel);
        row.add(text, BorderLayout.CENTER);

        // 右：打开文件夹 / 删除
        JButton openFolderBtn = new JButton("打开文件夹");
        openFolderBtn.setEnabled(exists);
        openFolderBtn.addActionListener(e -> openInFileManager(path));
        JButton delBtn = new JButton("删除");
        delBtn.addActionListener(e -> {
            config.removeRecentVault(path);
            list.remove(row);
            list.revalidate();
            list.repaint();
            if (path.equals(config.lastVault())) {
                config.setLastVault(null);
            }
        });
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btns.setOpaque(false);
        btns.add(openFolderBtn);
        btns.add(delBtn);
        row.add(btns, BorderLayout.EAST);

        // 单击行 → 打开该仓库解锁（按钮点击不冒泡，不受影响）
        row.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 1) {
                    openForUnlock(Path.of(path));
                }
            }
        });
        return row;
    }

    /** 目录名（历史项显示名）。 */
    private static String dirName(String path) {
        Path p = Path.of(path);
        Path n = p.getFileName();
        return n == null ? path : n.toString();
    }

    /** 简化路径：home 前缀替换为 ~，段数过多时中间省略。 */
    private static String simplifyPath(String path) {
        String home = System.getProperty("user.home");
        String p = path;
        if (home != null && p.startsWith(home)) {
            p = "~" + p.substring(home.length());
        }
        String[] parts = p.split("/");
        if (parts.length > 4) {
            p = parts[0] + "/…/" + parts[parts.length - 2] + "/" + parts[parts.length - 1];
        }
        return p;
    }

    /** 在系统文件管理器中打开目录（Desktop.open，跨平台 fallback）。 */
    private static void openInFileManager(String path) {
        try {
            java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
            if (desktop.isSupported(java.awt.Desktop.Action.OPEN)) {
                desktop.open(new java.io.File(path));
                return;
            }
        } catch (Exception ignore) {
        }
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("mac")) {
                pb = new ProcessBuilder("open", path);
            } else if (os.contains("win")) {
                pb = new ProcessBuilder("explorer", path);
            } else {
                pb = new ProcessBuilder("xdg-open", path);
            }
            pb.start();
        } catch (Exception ignore) {
        }
    }

    /** 应用形态：把某仓库带入解锁页（记录最近打开）。打开语义：仓库必须已存在。 */
    private void openForUnlock(Path root) {
        Path dataRoot = com.flora.sanctum.app.bootstrap.VaultDetector.dataDir(root);
        if (dataRoot == null) {
            dataRoot = root;
        }
        config.addRecentVault(root.toAbsolutePath().toString());
        unlockIsCreate = false;
        showUnlockPage(dataRoot);
    }

    // ---- 新建 / 导入 / 打开（原 SelectScreen 入口，合并进历史页） ----

    private void doNewVault() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("选择新建仓库的目录");
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path dir = chooser.getSelectedFile().toPath();
        try {
            // 默认构建普通仓库；需要独立运行可在设置页"独立运行"中配置
            Path root = com.flora.sanctum.app.bootstrap.RepoCreator.createNormal(dir);
            unlockIsCreate = true;
            showUnlockPage(root);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "创建失败：" + ex.getMessage(), "错误",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void doImportVault() {
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
            com.flora.sanctum.app.bootstrap.RepoImporter.Result result =
                    com.flora.sanctum.app.bootstrap.RepoImporter.importRemote(url, Path.of(local));
            if (result.vaultRoot != null) {
                showUnlockPage(result.vaultRoot);
            }
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(frame, e.getMessage(), "导入失败", JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "导入失败：" + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void doOpenVault() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            Path dir = chooser.getSelectedFile().toPath();
            if (com.flora.sanctum.app.bootstrap.VaultDetector.detect(dir)
                    == com.flora.sanctum.app.bootstrap.VaultDetector.Type.NOT_A_VAULT) {
                JOptionPane.showMessageDialog(frame, "该目录不是 flora-sanctum 仓库", "打开失败",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            openForUnlock(dir);
        }
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

    // ================= 解锁页（针对特定仓库） =================

    private JPanel buildUnlockPanel(Path root) {
        // 垂直居中的紧凑卡片：标题（含库路径副标题）→ 密码行 → 解锁 → 提示/返回
        JPanel panel = new UiTheme.PaperPanel(new java.awt.GridBagLayout());
        panel.setBorder(new EmptyBorder(16, 24, 16, 24));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(3, 0, 3, 0);

        String name = root == null ? "" : root.getFileName().toString();
        String path = root == null ? "" : root.toString();

        // 顶部弹性（内容垂直居中）
        c.gridy = 0;
        c.weighty = 1.0;
        panel.add(javax.swing.Box.createVerticalGlue(), c);

        // 仓库字母图标（与历史列表同一规则生成）
        JLabel icon = new JLabel(new LetterIcon(name, 64));
        icon.setHorizontalAlignment(JLabel.CENTER);
        c.gridy = 1;
        c.weighty = 0;
        c.insets = new Insets(0, 0, 10, 0);
        panel.add(icon, c);

        JLabel title = new JLabel("<html><div style='text-align:center'>" + name + "</div>"
                + "<div style='text-align:center;color:#8E9196;font-size:10pt'>" + path + "</div></html>");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        title.setHorizontalAlignment(JLabel.CENTER);
        c.gridy = 2;
        c.weighty = 0;
        c.insets = new Insets(0, 0, 18, 0);
        panel.add(title, c);

        // 主密码行
        JPanel pwRow = new JPanel(new BorderLayout(8, 0));
        pwRow.setOpaque(false);
        pwRow.add(new JLabel("主密码："), BorderLayout.WEST);
        JPasswordField pwField = new JPasswordField();
        pwRow.add(pwField, BorderLayout.CENTER);
        c.gridy = 3;
        c.insets = new Insets(3, 0, 3, 0);
        panel.add(pwRow, c);

        // 新建库时允许自定义 Argon2id 强度（默认高安全档），折叠于「高级」下
        final KdfParamsPanel kdfPanel = unlockIsCreate ? new KdfParamsPanel() : null;
        if (kdfPanel != null) {
            c.gridy = 4;
            c.insets = new Insets(6, 0, 3, 0);
            panel.add(kdfPanel, c);
        }

        // 解锁 / 创建按钮（与"回到历史列表"等宽居中；转圈以 JLayer 画在按钮右内侧，不占排版）
        JButton unlockBtn = new JButton(unlockIsCreate ? "创建并解锁" : "解锁");
        unlockBtn.setPreferredSize(new Dimension(170, 32));
        SpinnerIcon spinner = new SpinnerIcon(26);
        javax.swing.JLayer<JButton> unlockLayer = new javax.swing.JLayer<>(unlockBtn,
                new UnlockSpinnerUI(spinner));
        javax.swing.Timer spinnerTimer = new javax.swing.Timer(50, e -> {
            spinner.angle += 24;
            unlockLayer.repaint();
        });
        c.gridy = 4;
        c.fill = java.awt.GridBagConstraints.NONE;
        c.anchor = java.awt.GridBagConstraints.CENTER;
        panel.add(unlockLayer, c);
        c.fill = java.awt.GridBagConstraints.HORIZONTAL;

        JLabel error = new JLabel("", JLabel.CENTER);
        error.setForeground(java.awt.Color.RED.darker());
        c.gridy = 5;
        panel.add(error, c);

        // 应用形态：提供"回到历史列表"入口；独立形态不提供
        if (!standalone) {
            JButton backBtn = new JButton("回到历史列表");
            backBtn.setToolTipText("返回历史仓库列表页");
            backBtn.setPreferredSize(new Dimension(170, 32));
            backBtn.addActionListener(e -> showHistoryPage());
            c.gridy = 6;
            c.fill = java.awt.GridBagConstraints.NONE;
            c.anchor = java.awt.GridBagConstraints.CENTER;
            panel.add(backBtn, c);
            c.fill = java.awt.GridBagConstraints.HORIZONTAL;
        }

        // 底部弹性（内容垂直居中）
        c.gridy = 7;
        c.weighty = 1.0;
        panel.add(javax.swing.Box.createVerticalGlue(), c);

        java.util.function.Consumer<JPasswordField> unlock = f ->
                doUnlock(root, f, error, unlockLayer, unlockBtn, spinnerTimer, kdfPanel);
        unlockBtn.addActionListener(e -> unlock.accept(pwField));
        pwField.addActionListener(e -> unlock.accept(pwField));

        pwField.requestFocusInWindow();
        return panel;
    }

    private void doUnlock(Path root, JPasswordField pwField, JLabel error,
                          javax.swing.JLayer<JButton> unlockLayer,
                          JButton unlockBtn, javax.swing.Timer spinnerTimer,
                          KdfParamsPanel kdfPanel) {
        char[] pw = pwField.getPassword();
        if (pw.length == 0) {
            error.setText("请输入主密码");
            return;
        }
        char[] pwCopy = pw.clone();
        java.util.Arrays.fill(pw, (char) 0);
        // 新库时从「高级」面板读取 KDF 参数（非法/为空则回退默认档）
        int[] kdf = kdfPanel == null ? null : kdfPanel.resolve();
        // 转圈（JLayer 画在按钮右内侧）+ 禁用控件
        error.setText("");
        unlockLayer.putClientProperty("spinner.visible", Boolean.TRUE);
        unlockLayer.repaint();
        spinnerTimer.start();
        unlockBtn.setEnabled(false);
        pwField.setEnabled(false);
        // 后台线程解锁（Argon2 派生耗时长，避免阻塞 EDT 导致转圈不转）
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            final Sanctum[] result = new Sanctum[1];
            final String[] failMsg = new String[1];
            try {
                if (unlockIsCreate) {
                    // 新建：显式创建并解锁（KDF 参数自定义或默认档）
                    if (kdf == null) {
                        result[0] = Sanctum.createAndUnlock(root, pwCopy);
                    } else {
                        result[0] = Sanctum.createAndUnlock(root, pwCopy,
                                kdf[0], kdf[1], kdf[2]);
                    }
                } else {
                    // 打开：仓库必须已存在，失败不自动新建
                    if (!Files.exists(root)) {
                        failMsg[0] = "仓库不存在或已被删除";
                    } else {
                        result[0] = Sanctum.open(root);
                        result[0].unlock(pwCopy);
                    }
                }
            } catch (Exception ex) {
                failMsg[0] = "解锁失败";
            } finally {
                java.util.Arrays.fill(pwCopy, (char) 0);
            }
            final Sanctum s = result[0];
            final String msg = failMsg[0];
            SwingUtilities.invokeLater(() -> {
                unlockLayer.putClientProperty("spinner.visible", Boolean.FALSE);
                unlockLayer.repaint();
                spinnerTimer.stop();
                unlockBtn.setEnabled(true);
                pwField.setEnabled(true);
                if (msg != null) {
                    error.setText(msg);
                    return;
                }
                sanctum = s;
                unlockedVaultPath = root.toAbsolutePath().toString();
                config.addRecentVault(unlockedVaultPath);
                config.setLastVault(unlockedVaultPath);
                frame.setTitle("flora-sanctum(" + root.getFileName() + ")");
                current.set(sanctum);
                applyTheme(sanctum.config().theme()); // 解锁后应用仓库主题
                showEditPage();
                startAutoLockTimer();
            });
        });
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
        }, sanctum.config().lockTimeoutSeconds() * 1000L);
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
        unlockedVaultPath = null;
        stopTimers();
        frame.setTitle("flora-sanctum");
        // 锁定后直接回到该仓库的解锁页（不退回历史列表）；独立形态同样。此时是"打开"语义（库已存在）
        if (targetVaultRoot != null) {
            unlockIsCreate = false;
            showUnlockPage(targetVaultRoot);
        } else {
            showHistoryPage();
        }
    }

    // ================= 主界面 =================

    private JPanel buildMainPanel() {
        // 中间区域用暖纸底色（openhanako Warm Paper）；工具栏略深，左右卡片更白
        JPanel root = new UiTheme.PaperPanel(new BorderLayout());

        // 顶部工具栏（SVG 图标按钮 + tooltip，去文字标签；尺寸 24→29 ≈ +20%）
        newEntryBtn = iconButton(SvgIcon.get("ui/new-entry", 29), "新建条目");
        newGroupBtn = iconButton(SvgIcon.get("ui/new-group", 29), "新建文件夹");
        delBtn = iconButton(SvgIcon.get("ui/trash", 29), "删除");
        syncBtn = iconButton(SvgIcon.get("ui/sync", 29), "同步");
        settingsBtn = iconButton(SvgIcon.get("ui/settings", 29), "设置");
        lockBtn = iconButton(SvgIcon.get("ui/lock", 29), "锁定");
        JButton importBtn = new JButton("导入");
        importBtn.setToolTipText("从 KeePassXC / KeePass (KDBX4) 数据库导入");
        importBtn.addActionListener(e -> doImportKdbx());
        statusLabel = new JLabel();
        syncBtn.setVisible(isFullyManaged());

        JTextField searchField = new JTextField(14);
        searchFieldRef = searchField;
        searchField.setToolTipText("按名称/字段搜索条目");
        JButton clearSearch = new JButton("×");
        clearSearch.setPreferredSize(new Dimension(26, 24));
        clearSearch.setMargin(new Insets(0, 0, 0, 0));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        top.setOpaque(true);
        top.setBackground(UiTheme.PAPER_LIGHT); // 工具栏略深于中间底色
        top.add(newEntryBtn);
        top.add(newGroupBtn);
        top.add(delBtn);
        top.add(syncBtn);
        top.add(settingsBtn);
        top.add(lockBtn);
        top.add(importBtn);
        top.add(new JLabel("搜索:"));
        top.add(searchField);
        top.add(clearSearch);
        top.add(statusLabel);
        // 按钮栏与下方内容之间加 1px 分割线
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(top, BorderLayout.NORTH);
        JPanel hLine = new JPanel();
        hLine.setOpaque(true);
        hLine.setBackground(UiTheme.DIVIDER);
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
            Object sel = currentSelection();
            // 选中垃圾桶节点 → 右侧只读详情，不刷新条目列表
            if (sel instanceof UUID u && isTrashSelection()) {
                renderTrashNode(u);
                return;
            }
            refreshEntryList(searchField.getText());
            // 选中文件夹 → 右侧编辑面板显示文件夹编辑
            if (sel instanceof UUID u && typeOf(u) == StoredNodeType.GROUP) {
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
        // 左栏：方形侧边栏（略深底色，无圆角）
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setOpaque(true);
        leftPanel.setBackground(UiTheme.PAPER_LIGHT);
        leftPanel.add(treeScroll, BorderLayout.CENTER);

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
                    if (idx >= 0 && idx < listItemTypes.size() && listItemTypes.get(idx) == StoredNodeType.GROUP) {
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

        // 搜索视图横幅（黄色，搜索时显示，区分"搜索视图"与"文件夹内容"）
        searchBanner = new JLabel();
        searchBanner.setOpaque(true);
        searchBanner.setBackground(new java.awt.Color(0xFF, 0xF3, 0xCD));
        searchBanner.setForeground(new java.awt.Color(0x8A, 0x6D, 0x3B));
        searchBanner.setBorder(new javax.swing.border.EmptyBorder(4, 10, 4, 10));
        searchBanner.setVisible(false);
        JPanel entryPanel = new JPanel(new BorderLayout());
        entryPanel.setOpaque(false);
        entryPanel.add(searchBanner, BorderLayout.NORTH);
        entryPanel.add(entryScroll, BorderLayout.CENTER);

        // 右：编辑面板（无容器边框，区域间只保留 JSplitPane 一条分界线）
        editPanel = new JPanel();
        editPanel.setLayout(new BoxLayout(editPanel, BoxLayout.Y_AXIS));
        editPanel.setBorder(new javax.swing.border.EmptyBorder(8, 10, 8, 10));
        editPanel.setOpaque(false);
        JScrollPane editScroll = new JScrollPane(editPanel);
        editScroll.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        editScroll.setOpaque(false);
        editScroll.getViewport().setOpaque(false);
        // 编辑面板圆角悬浮卡片
        CardPanel editCard = new CardPanel(new BorderLayout(), 10);
        editCard.add(editScroll, BorderLayout.CENTER);

        JSplitPane rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, entryPanel, editCard);
        rightSplit.setDividerSize(0); // 去除右边卡片与中间列表之间的分割线
        rightSplit.setDividerLocation(280);
        keepDividerRatio(rightSplit, "ui.divider.right", 280);
        rightSplit.setOpaque(false);
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightSplit);
        mainSplit.setDividerLocation(220);
        keepDividerRatio(mainSplit, "ui.divider.main", 220);
        mainSplit.setOpaque(false);
        root.add(mainSplit, BorderLayout.CENTER);

        newEntryBtn.addActionListener(e -> doNewEntry());
        newGroupBtn.addActionListener(e -> doNewGroup());
        delBtn.addActionListener(e -> doDelete());
        syncBtn.addActionListener(e -> doSync());
        settingsBtn.addActionListener(e -> openSettings());
        lockBtn.addActionListener(e -> lock());
        searchField.addActionListener(e -> refreshEntryList(searchField.getText()));
        clearSearch.addActionListener(e -> {
            searchField.setText("");
            refreshEntryList("");
        });
        updateToolbar();
        return root;
    }

    /** 根据当前树选择切换工具栏按钮可用性。 */
    private void updateToolbar() {
        ViewNodeType section = sectionOf(currentSelection());
        // 选中垃圾桶节点 → 只读，禁用新建/删除
        if (isTrashSelection()) {
            newEntryBtn.setEnabled(false);
            newGroupBtn.setEnabled(false);
            delBtn.setEnabled(false);
            return;
        }
        // 新建条目/文件夹：仅密码库文件夹上下文可用
        boolean objectsCtx = section == null && currentGroupId() != null; // 选中了普通文件夹
        boolean objectsRoot = ViewNodeType.PASSWORD == section; // 密码库根（可建文件夹）
        newEntryBtn.setEnabled(objectsCtx);
        newGroupBtn.setEnabled(objectsCtx || objectsRoot);
        delBtn.setEnabled(true);
    }

    private boolean isFullyManaged() {
        return sanctum != null
                && new com.flora.sanctum.app.sync.SyncService(sanctum.root()).isFullyManaged();
    }

    // ---- 组树 ----

    /** 树节点类型：普通文件夹（UUID userObject）或区段节点（ViewNodeType userObject，对应树分类）。 */

    private void rebuildGroupTree() {
        treeRoot = new DefaultMutableTreeNode("全部");
        groupNodes.clear();
        groupCache = null; // 重置缓存
        trashView = sanctum.trash();

        // 四个区段节点（对应展示区段，见 ViewNodeType）
        DefaultMutableTreeNode objectsNode = new DefaultMutableTreeNode("密码库");
        objectsNode.setUserObject(ViewNodeType.PASSWORD);
        treeRoot.add(objectsNode);
        // objects 层级：顶层文件夹（ObjectTree 根组，已排除 root group）+ 递归子文件夹
        for (GroupNode g : sanctum.objectTree().rootGroups()) {
            addGroupNode(objectsNode, g.uuid(), g.name());
        }

        // 垃圾桶虚拟根（与数据根平级，无对应存储；见设计 idea20260826-sanctum-trash）
        DefaultMutableTreeNode trashNode = new DefaultMutableTreeNode("垃圾桶");
        trashNode.setUserObject(ViewNodeType.TRASH);
        treeRoot.add(trashNode);
        for (TrashCategory cat : TrashCategory.values()) {
            DefaultMutableTreeNode catNode = new DefaultMutableTreeNode(cat.label);
            catNode.setUserObject(cat);
            trashNode.add(catNode);
            for (UUID id : trashUuids(cat.kind)) {
                DefaultMutableTreeNode leaf = new DefaultMutableTreeNode(nodeName(id));
                leaf.setUserObject(id);
                catNode.add(leaf);
            }
        }

        groupTree.setModel(new DefaultTreeModel(treeRoot));
        // 根隐藏，密码库区段展开
        for (int r = 0; r < groupTree.getRowCount(); r++) {
            groupTree.expandRow(r);
        }
    }

    /** 某类垃圾桶节点的 uuid 列表。 */
    private List<UUID> trashUuids(com.flora.sanctum.model.TrashView.TrashKind kind) {
        if (trashView == null) {
            return List.of();
        }
        return switch (kind) {
            case MANUAL -> trashView.manual();
            case UNREACHABLE -> trashView.unreachable();
            case UNLOCKABLE -> trashView.unlockable();
        };
    }

    /** 跨树按 uuid 取展示名（组/条目/图标/SSH/远程）；未知显示 uuid 前 8 位。 */
    private String nodeName(UUID uuid) {
        TreeNode n = sanctum.findNode(uuid);
        String name = null;
        if (n instanceof GroupNode g) {
            name = g.name();
        } else if (n instanceof EntryNode en) {
            name = en.name();
        } else if (n instanceof IconNode ic) {
            name = ic.name();
        } else if (n instanceof SshKeyNode k) {
            name = k.name();
        } else if (n instanceof RemoteNode r) {
            name = r.name();
        } else if (n != null) {
            JsonObject d = n.data();
            name = d == null ? null : d.getString("name");
        }
        if (name == null || name.isBlank()) {
            return "未知(" + uuid.toString().substring(0, 8) + ")";
        }
        return name;
    }

    /** 当前选中节点是否落在垃圾桶某类中。 */
    private boolean isTrashSelection() {
        Object sel = currentSelection();
        if (sel instanceof UUID u && trashView != null) {
            return trashView.contains(u);
        }
        return false;
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
                    groupCache.put(g.uuid(), new String[]{g.parentRef(), g.name()});
                }
            }
        }
        return groupCache;
    }

    /** 文件夹设置的图标 id（无则 null）。 */
    private String groupIconOf(UUID groupUuid) {
        GroupNode g = sanctum.objectTree().group(groupUuid);
        return g == null ? null : g.iconRef();
    }

    // ---- 条目列表 ----

    private void refreshEntryList(String filter) {
        entryModel.clear();
        entryUuids.clear();
        listItemTypes.clear();
        listItemIcons.clear();
        String q = filter == null ? "" : filter.trim().toLowerCase();
        Object sel = currentSelection();
        ViewNodeType section = sectionOf(sel);
        UUID groupId = section == null ? groupIdOf(sel) : null;

        // 全局搜索：搜索非空时跨区段/文件夹搜索所有条目（不分当前选择）
        if (!q.isEmpty()) {
            for (TreeNode n : sanctum.objectTree().nodes()) {
                if (n instanceof EntryNode e && matchesFilter(e, q)) {
                    String path = folderPathOf(e);
                    entryModel.addElement(path.isEmpty() ? e.name() : e.name() + "  ·  " + path);
                    entryUuids.add(e.uuid());
                    listItemTypes.add(StoredNodeType.ENTRY);
                    listItemIcons.add(e.iconRef());
                }
            }
            if (searchBanner != null) {
                searchBanner.setText("搜索视图 · " + entryModel.size() + " 条结果");
                searchBanner.setVisible(true);
            }
            return;
        }
        if (searchBanner != null) {
            searchBanner.setVisible(false);
        }

        if (ViewNodeType.ICON == section) {
            for (IconNode icon : sanctum.iconTree().icons()) {
                entryModel.addElement(iconLabel(icon));
                entryUuids.add(icon.uuid());
                listItemTypes.add(StoredNodeType.ICON);
                listItemIcons.add(icon.uuid().toString());
            }
            return;
        }
        if (ViewNodeType.SSH_KEY == section) {
            for (SshKeyNode key : sanctum.sshKeyTree().keys()) {
                entryModel.addElement(key.name());
                entryUuids.add(key.uuid());
                listItemTypes.add(StoredNodeType.SSH_KEY);
                listItemIcons.add(null);
            }
            return;
        }
        if (ViewNodeType.REMOTE == section) {
            for (RemoteNode r : sanctum.remoteTree().remotes()) {
                entryModel.addElement(r.name());
                entryUuids.add(r.uuid());
                listItemTypes.add(StoredNodeType.REMOTE);
                listItemIcons.add(null);
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
                listItemTypes.add(StoredNodeType.GROUP);
                listItemIcons.add(g.iconRef());
            } else if (n instanceof EntryNode e) {
                if (!q.isEmpty() && !matchesFilter(e, q)) {
                    continue;
                }
                entryModel.addElement(e.name());
                entryUuids.add(e.uuid());
                listItemTypes.add(StoredNodeType.ENTRY);
                listItemIcons.add(e.iconRef());
            }
        }
    }

    /** 图标区段条目显示名（有 name 用 name，否则格式提示）。 */
    private String iconLabel(IconNode icon) {
        String name = icon.name();
        if (name != null && !name.isBlank()) {
            return name;
        }
        String format = icon.format();
        return (format == null ? "图标" : "图标 [" + format + "]");
    }

    // ---- 内置图标库（/icons/library/*.svg）与选择 ----

    private static final String BUILTIN_PREFIX = "builtin:";

    private static String builtinIconId(String name) {
        return BUILTIN_PREFIX + name;
    }

    private static boolean isBuiltinIcon(String id) {
        return id != null && id.startsWith(BUILTIN_PREFIX);
    }

    private static String builtinName(String id) {
        return id == null ? "" : id.substring(BUILTIN_PREFIX.length());
    }

    /** 设置页图标详情：缩略图 + 名称。 */
    private void renderSettingsIcon(String id, JPanel target) {
        target.removeAll();
        Icon ic = iconById(id, 48);
        if (ic != null) {
            JLabel pic = new JLabel(ic);
            pic.setAlignmentX(0f);
            target.add(pic);
            target.add(javax.swing.Box.createVerticalStrut(6));
        }
        addInfoLabel(isBuiltinIcon(id) ? "内置图标：" + builtinName(id) : "用户导入图标", target);
        target.revalidate();
        target.repaint();
    }

    /** 按图标 id 渲染 Icon（内置 SvgIcon / 用户 iconTree 字节），失败返回 null。 */
    private Icon iconById(String id, int size) {
        if (isBuiltinIcon(id)) {
            Icon ic = SvgIcon.get("library/" + id.substring(BUILTIN_PREFIX.length()), size);
            return ic != null ? ic : SvgIcon.get("ui/entry", size);
        }
        if (id != null) {
            try {
                IconNode node = sanctum.iconTree().find(UUID.fromString(id));
                if (node != null) {
                    byte[] data = node.iconData();
                    if ("svg".equalsIgnoreCase(node.format())) {
                        Icon ic = SvgIcon.fromBytes(data, size);
                        if (ic != null) {
                            return ic;
                        }
                    } else if (data.length > 0) {
                        java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(
                                new java.io.ByteArrayInputStream(data));
                        if (img != null) {
                            return new javax.swing.ImageIcon(
                                    img.getScaledInstance(size, size, java.awt.Image.SCALE_SMOOTH));
                        }
                    }
                }
            } catch (Exception ignore) {
            }
        }
        return null;
    }

    /** 弹窗网格选择图标（内置 + 用户），选中后回调 id（"builtin:name" 或 uuid 字符串）。 */
    private void chooseIconDialog(String title, java.util.function.Consumer<String> onPick) {
        JDialog dialog = new JDialog(frame, title, true);
        JPanel grid = new JPanel(new GridLayout(0, 5, 8, 8));
        grid.setOpaque(false);
        grid.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        for (String name : SvgIcon.libraryIcons()) {
            String id = builtinIconId(name);
            grid.add(makeIconCell(iconById(id, 32), () -> {
                onPick.accept(id);
                dialog.dispose();
            }));
        }
        for (IconNode node : sanctum.iconTree().icons()) {
            String id = node.uuid().toString();
            grid.add(makeIconCell(iconById(id, 32), () -> {
                onPick.accept(id);
                dialog.dispose();
            }));
        }
        if (grid.getComponentCount() == 0) {
            grid.add(new JLabel("图标库为空"));
        }
        dialog.setContentPane(new JScrollPane(grid));
        dialog.pack();
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    private JButton makeIconCell(Icon icon, Runnable action) {
        JButton b = new JButton(icon);
        b.setPreferredSize(new Dimension(48, 48));
        b.addActionListener(e -> action.run());
        return b;
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

    /** 条目所属文件夹路径（如"社交/工作"），顶层返回空串。 */
    private String folderPathOf(EntryNode e) {
        String parent = e.parentRef();
        if (isTopLevel(parent)) {
            return "";
        }
        List<String> names = new ArrayList<>();
        String cur = parent;
        while (cur != null && !isTopLevel(cur)) {
            UUID id;
            try {
                id = UUID.fromString(cur);
            } catch (IllegalArgumentException ex) {
                break;
            }
            String[] info = groupsById().get(id);
            if (info == null) {
                break;
            }
            names.add(0, info[1] == null || info[1].isBlank() ? "未命名" : info[1]);
            cur = info[0];
        }
        return String.join("/", names);
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

    /** 若当前选中是区段节点（ViewNodeType userObject）则返回，否则 null。 */
    private ViewNodeType sectionOf(Object sel) {
        return sel instanceof ViewNodeType t ? t : null;
    }

    /** 区段展示名（对象树区段与设置区段等）。 */
    private static String sectionDisplayName(ViewNodeType tag) {
        return switch (tag) {
            case ICON -> "图标";
            case SSH_KEY -> "SSH 密钥";
            case REMOTE -> "远程";
            case PASSWORD -> "密码库";
            default -> "设置";
        };
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
        StoredNodeType type = typeOf(u);
        if (type == StoredNodeType.GROUP) {
            renderGroupPanel(u);
        } else if (type == StoredNodeType.ICON) {
            renderIconPanel(u, editPanel);
        } else if (type == StoredNodeType.SSH_KEY) {
            renderSshKeyPanel(u, editPanel);
        } else if (type == StoredNodeType.REMOTE || type == StoredNodeType.FIELD) {
            renderRemotePanel(u, editPanel);
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

    /** 图标详情面板：预览图片 + 提示。渲染到指定目标面板。 */
    private void renderIconPanel(UUID iconUuid, JPanel target) {
        target.removeAll();
        IconNode icon = sanctum.iconTree().find(iconUuid);
        addInfoLabel("图标 [格式 " + (icon == null ? "?" : icon.format()) + "]", target);
        addInfoLabel("自定义图标，可在条目编辑中选择使用", target);
        target.revalidate();
        target.repaint();
    }

    /** SSH 密钥详情面板。渲染到指定目标面板。 */
    private void renderSshKeyPanel(UUID keyUuid, JPanel target) {
        target.removeAll();
        SshKeyNode key = sanctum.sshKeyTree().find(keyUuid);
        addInfoLabel("SSH 密钥：" + (key == null ? "?" : key.name()), target);
        addInfoLabel("私钥已加密存储", target);
        target.revalidate();
        target.repaint();
    }

    /** 远程配置详情面板。渲染到指定目标面板。 */
    private void renderRemotePanel(UUID remoteUuid, JPanel target) {
        target.removeAll();
        RemoteNode remote = sanctum.remoteTree().find(remoteUuid);
        addInfoLabel("远程：" + (remote == null ? "?" : remote.name()), target);
        target.revalidate();
        target.repaint();
    }

    /** 添加一行受 max height 约束的只读信息标签。 */
    private void addInfoLabel(String text, JPanel target) {
        JLabel label = new JLabel(text);
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        target.add(label);
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
        nameRow.setOpaque(false);
        nameRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        JLabel nameTag = new JLabel("名称 :");
        nameTag.setFont(nameTag.getFont().deriveFont(Font.BOLD, 14f));
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
            int ok = JOptionPane.showConfirmDialog(frame, "删除该文件夹及其内容（移入垃圾桶，可恢复）?", "确认", JOptionPane.YES_NO_OPTION);
            if (ok == JOptionPane.YES_OPTION) {
                resetAutoLock();
                GroupNode g = sanctum.objectTree().group(groupUuid);
                if (g != null) {
                    g.markDeleted();
                }
                rebuildGroupTree();
                refreshEntryList(currentSearchQuery());
                clearEditPanel();
            }
        });
        JButton iconBtn = new JButton("选择图标");
        iconBtn.addActionListener(e -> chooseIconDialog("文件夹图标", id -> {
            resetAutoLock();
            try {
                group.setIcon(id);
                rebuildGroupTree();
                renderGroupPanel(groupUuid);
                statusLabel.setText("已设置图标");
            } catch (Exception ex) {
                statusLabel.setText("设置图标失败");
            }
        }));
        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        actionRow.setOpaque(false);
        actionRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        actionRow.add(saveBtn);
        actionRow.add(delGroupBtn);
        actionRow.add(iconBtn);
        editPanel.add(actionRow);
        editPanel.revalidate();
        editPanel.repaint();
    }

    /** 垃圾桶节点只读详情面板：标注删除类型 + 原位置，内容只读（名称/字段不可改，无操作按钮）。 */
    private void renderTrashNode(UUID uuid) {
        editPanel.removeAll();
        if (trashView == null) {
            return;
        }
        com.flora.sanctum.model.TrashView.TrashKind kind = trashView.kindOf(uuid);
        // 类型标签
        JLabel typeTag = new JLabel("垃圾桶 · " + (kind == null ? "未知" : kind.label()));
        typeTag.setFont(typeTag.getFont().deriveFont(Font.BOLD, 14f));
        typeTag.setForeground(new java.awt.Color(150, 90, 40));
        editPanel.add(typeTag);
        // 原位置（临时计算）
        editPanel.add(makeInfoRow("原位置", trashView.originalPath(uuid)));

        StoredNodeType type = typeOf(uuid);
        TreeNode node = sanctum.findNode(uuid);
        if (type == StoredNodeType.GROUP) {
            GroupNode g = (GroupNode) node;
            editPanel.add(makeInfoRow("名称", g == null ? null : g.name()));
        } else if (type == StoredNodeType.ENTRY) {
            EntryNode en = (EntryNode) node;
            if (en == null) {
                editPanel.add(makeInfoRow("状态", "无法解密（不可解锁）"));
            } else {
                editPanel.add(makeInfoRow("名称", en.name()));
                editPanel.add(makeInfoRow("URL", en.url()));
                editPanel.add(makeInfoRow("用户名", en.username()));
                String pw = en.password();
                editPanel.add(makeInfoRow("密码", pw == null ? "" : "••••••（见复制按钮）"));
                JButton copyBtn = new JButton("复制密码");
                copyBtn.addActionListener(e -> copyPassword(uuid));
                JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
                actionRow.setOpaque(false);
                actionRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
                actionRow.add(copyBtn);
                editPanel.add(actionRow);
            }
        } else if (node != null && node.data() != null) {
            editPanel.add(makeInfoRow("名称", node.data().getString("name")));
        } else {
            editPanel.add(makeInfoRow("状态", "无法解密（不可解锁）"));
        }
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
            row.setOpaque(false);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
            JLabel fLabel = new JLabel((fn == null ? "" : fn) + " :");
            fLabel.setPreferredSize(new Dimension(110, 24));
            JTextField fValue = makeEntryField(val);
            row.add(fLabel, BorderLayout.WEST);
            row.add(fValue, BorderLayout.CENTER);
            // EAST：kind 下拉 + 删除按钮
            JPanel east = new JPanel();
            east.setOpaque(false);
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
        actionRow.setOpaque(false);
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

    /**
     * JSplitPane 分隔线比例记忆（持久化到全局配置，非机密信息）：
     * 拖动时按当前比例保存（key）；resize 时按最新比例重设，避免全屏/窗口切换后位置失衡。
     * 首次布局用配置中的比例；无则用默认像素换算。
     */
    private void keepDividerRatio(JSplitPane split, String key, int defaultPx) {
        split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> {
            int w = split.getWidth();
            int loc = split.getDividerLocation();
            if (w > 0 && loc > 0) {
                config.setDividerRatio(key, loc / (double) w);
            }
        });
        split.addComponentListener(new java.awt.event.ComponentAdapter() {
            private Double ratio;

            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                // resize 事件触发时宽度可能还是旧值，延迟到布局完成后读取最新宽度，
                // 否则用旧宽算出的位置在新宽度下比例会偏移（最大化/还原不一致的根因）。
                SwingUtilities.invokeLater(() -> {
                    int w = split.getWidth();
                    if (w <= 0) {
                        return;
                    }
                    Double saved = config.dividerRatio(key);
                    if (ratio == null) {
                        ratio = saved != null ? saved : defaultPx / (double) w;
                    } else if (saved != null && !saved.equals(ratio)) {
                        ratio = saved; // 拖动后采用新比例
                    }
                    split.setDividerLocation((int) Math.round(ratio * w));
                });
            }
        });
    }

    /** 构造一行：左标签 + 右文本字段（高度受限）。required=true 时标签标红加粗。 */
    private JPanel makeEntryRow(String label, JTextField field, boolean required) {
        JPanel row = new JPanel(new BorderLayout(0, 0));
        row.setOpaque(false); // 透明透出卡片背景，与编辑栏对齐
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
        row.setOpaque(false); // 透明透出卡片背景，与编辑栏对齐
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
            if (com.flora.sanctum.model.EntryFields.isPreset(fn)) {
                statusLabel.setText("预设字段名不可用于自定义字段");
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

    /** 由条目推导其所属组 uuid（顶层条目 parent 为根对象 uuid 返回 null）。 */
    private UUID groupIdOf(UUID entryUuid) {
        EntryNode entry = sanctum.objectTree().entry(entryUuid);
        if (entry == null) {
            return null;
        }
        String p = entry.parentRef();
        return isTopLevel(p) ? null : UUID.fromString(p);
    }

    /** parent 是否为顶层（根对象 uuid / null）。 */
    private boolean isTopLevel(String parent) {
        if (parent == null) {
            return true;
        }
        UUID rootUuid = sanctum.rootObjectUuid();
        return rootUuid != null && rootUuid.toString().equals(parent);
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
    /** 搜索视图横幅（中间区域上方，黄色；搜索时显示）。 */
    private JLabel searchBanner;
    /** 当前窗口尺寸的存储 key（"guide" 引导页 / "main" 主页面），resize 时即时保存。 */
    private String currentWindowKey;
    // ---- 设置页三栏 ----
    private JTree settingsTree;
    private JList<String> settingsEntryList;
    private DefaultListModel<String> settingsEntryModel;
    private final List<String> settingsEntryIds = new ArrayList<>();
    private final List<String> settingsEntryKeys = new ArrayList<>();
    private final List<SettingsKind> settingsEntryKinds = new ArrayList<>();
    private JPanel settingsEditPanel;
    private javax.swing.JComboBox<String> settingsThemeCombo;
    private JTextField settingsLockField;
    private JTextField settingsClipField;
    private JButton newEntryBtn;
    private JButton newGroupBtn;
    private JButton delBtn;
    private JButton syncBtn;
    private JButton settingsBtn;
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
        }, sanctum.config().clipboardClearSeconds() * 1000L);
    }

    // ================= 图标 / SSH / 远程 =================

    /** 导入图片文件为自定义图标（icon root）。 */
    private void doImportImage() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "图片 (png/jpg/gif/svg)", "png", "jpg", "jpeg", "gif", "svg"));
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
                sanctum.iconTree().createIcon(name, data, "svg");
            } else {
                javax.imageio.ImageIO.read(file.toFile()); // 校验确为可读图片
                sanctum.iconTree().createIcon(name, data, format);
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

    /** 添加远程配置（type=remote 节点）。 */
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

    /** 为条目选择图标（内置 + 用户图标库）。 */
    private void chooseEntryIcon(UUID entryUuid) {
        chooseIconDialog("选择图标", id -> {
            resetAutoLock();
            try {
                EntryNode entry = sanctum.objectTree().entry(entryUuid);
                if (entry != null) {
                    entry.setIcon(id);
                    renderEntry(entryUuid);
                    statusLabel.setText("已设置图标");
                }
            } catch (Exception ex) {
                statusLabel.setText("设置图标失败");
            }
        });
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
        // 仅在密码库根（对象树区段）或普通文件夹下允许新建文件夹
        ViewNodeType section = sectionOf(sel);
        if (ViewNodeType.ICON == section || ViewNodeType.SSH_KEY == section || ViewNodeType.REMOTE == section) {
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

    /** 从 KeePassXC / KeePass (KDBX4) 导入：选文件 → 主密码 → 后台导入到当前仓库。 */
    private void doImportKdbx() {
        if (sanctum == null || sanctum.objectTree() == null) {
            statusLabel.setText("请先打开一个仓库");
            return;
        }
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "KeePass 数据库 (*.kdbx)", "kdbx"));
        if (fc.showOpenDialog(frame) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return;
        }
        java.nio.file.Path file = fc.getSelectedFile().toPath();
        java.util.Optional<com.flora.sanctum.app.io.importer.Importer> importer =
                com.flora.sanctum.app.io.importer.Importer.forFile(file);
        if (importer.isEmpty()) {
            javax.swing.JOptionPane.showMessageDialog(frame,
                    "不支持的文件类型（仅支持 .kdbx）", "导入", javax.swing.JOptionPane.WARNING_MESSAGE);
            return;
        }
        ImportCreds creds = askImportPassword(file);
        if (creds == null) {
            return; // 取消
        }
        statusLabel.setText("正在导入 " + file.getFileName() + " …");
        final com.flora.sanctum.app.io.importer.Importer imp = importer.get();
        new Thread(() -> {
            try {
                com.flora.sanctum.app.io.importer.ImportContext ctx =
                        com.flora.sanctum.app.io.importer.ImportContext.builder(sanctum.objectTree())
                                .password(creds.password)
                                .keyFile(creds.keyFile)
                                .build();
                com.flora.sanctum.app.io.importer.ImportResult result = imp.importFile(file, ctx);
                javax.swing.SwingUtilities.invokeLater(() -> {
                    rebuildGroupTree();
                    refreshEntryList(currentSearchQuery());
                    statusLabel.setText("导入完成：" + result);
                    javax.swing.JOptionPane.showMessageDialog(frame,
                            "导入完成：\n" + result, "导入", javax.swing.JOptionPane.INFORMATION_MESSAGE);
                });
            } catch (Exception ex) {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                String msg = cause instanceof com.flora.sanctum.app.io.importer.ImportException
                        ? cause.getMessage() : cause.toString();
                javax.swing.SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("导入失败");
                    javax.swing.JOptionPane.showMessageDialog(frame,
                            "导入失败：" + msg, "导入错误", javax.swing.JOptionPane.ERROR_MESSAGE);
                });
            } finally {
                if (creds.password != null) {
                    java.util.Arrays.fill(creds.password, '\0');
                }
            }
        }, "kdbx-import").start();
    }

    /** 导入主密码对话框（含可选密钥文件）。返回 null 表示取消。 */
    private ImportCreds askImportPassword(java.nio.file.Path file) {
        javax.swing.JDialog d = new javax.swing.JDialog(frame, "导入 " + file.getFileName(), true);
        d.setLayout(new java.awt.BorderLayout(10, 10));
        JPanel form = new JPanel(new java.awt.GridLayout(0, 1, 6, 6));
        javax.swing.JPasswordField pf = new javax.swing.JPasswordField();
        form.add(new javax.swing.JLabel("主密码："));
        form.add(pf);
        javax.swing.JButton keyBtn = new javax.swing.JButton("选择密钥文件（可选）");
        javax.swing.JLabel keyLabel = new javax.swing.JLabel("未选择密钥文件");
        final java.nio.file.Path[] keyFile = {null};
        keyBtn.addActionListener(e -> {
            javax.swing.JFileChooser kc = new javax.swing.JFileChooser();
            if (kc.showOpenDialog(d) == javax.swing.JFileChooser.APPROVE_OPTION) {
                keyFile[0] = kc.getSelectedFile().toPath();
                keyLabel.setText(keyFile[0].getFileName().toString());
            }
        });
        form.add(keyBtn);
        form.add(keyLabel);
        javax.swing.JButton ok = new javax.swing.JButton("导入");
        javax.swing.JButton cancel = new javax.swing.JButton("取消");
        java.util.concurrent.atomic.AtomicReference<char[]> result = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<java.nio.file.Path> kf = new java.util.concurrent.atomic.AtomicReference<>();
        ok.addActionListener(e -> {
            result.set(pf.getPassword());
            kf.set(keyFile[0]);
            d.dispose();
        });
        cancel.addActionListener(e -> d.dispose());
        javax.swing.JPanel btns = new javax.swing.JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
        btns.add(cancel);
        btns.add(ok);
        d.add(form, java.awt.BorderLayout.CENTER);
        d.add(btns, java.awt.BorderLayout.SOUTH);
        d.pack();
        d.setLocationRelativeTo(frame);
        d.setVisible(true);
        if (result.get() == null) {
            return null;
        }
        ImportCreds c = new ImportCreds();
        c.password = result.get();
        c.keyFile = kf.get();
        return c;
    }

    /** 导入凭据（主密码 + 可选密钥文件）。 */
    private static final class ImportCreds {
        char[] password;
        java.nio.file.Path keyFile;
    }

    private void doDelete() {
        Object sel = currentSelection();
        ViewNodeType section = sectionOf(sel);
        UUID entryUuid = selectedEntryUuid();
        if (entryUuid != null) {
            StoredNodeType type = typeOf(entryUuid);
            String what = switch (type == null ? StoredNodeType.ENTRY : type) {
                case GROUP -> "该文件夹";
                case ICON -> "该图标";
                case SSH_KEY -> "该 SSH 密钥";
                case REMOTE -> "该远程配置";
                default -> "该条目";
            };
            int ok = JOptionPane.showConfirmDialog(frame, "删除" + what + "（移入垃圾桶，可恢复）?", "确认", JOptionPane.YES_NO_OPTION);
            if (ok == JOptionPane.YES_OPTION) {
                resetAutoLock();
                TreeNode node = sanctum.findNode(entryUuid);
                if (node != null) {
                    node.markDeleted();
                }
                refreshEntryList(currentSearchQuery());
                rebuildGroupTree();
            }
            return;
        }
        // 区段根节点（图标/SSH/远程/密码库根/垃圾桶）不可删除
        if (section != null) {
            statusLabel.setText("根组不允许删除");
            return;
        }
        DefaultMutableTreeNode node = currentTreeNode();
        if (node != null && node != treeRoot && node.getUserObject() instanceof UUID groupId) {
            int ok = JOptionPane.showConfirmDialog(frame, "删除该文件夹及其内容（移入垃圾桶，可恢复）?", "确认", JOptionPane.YES_NO_OPTION);
            if (ok == JOptionPane.YES_OPTION) {
                GroupNode g = sanctum.objectTree().group(groupId);
                if (g != null) {
                    g.markDeleted();
                }
                rebuildGroupTree();
                refreshEntryList(currentSearchQuery());
                resetAutoLock();
            }
        }
    }

    /** 对象类型，未知返回 null。 */
    private StoredNodeType typeOf(UUID uuid) {
        TreeNode n = sanctum.findNode(uuid);
        return n == null ? null : n.type();
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
        showSettingsPage();
    }

    /** 漂浮提示（toast）：短暂显示后自动消失，不打断交互。 */
    private void showToast(String message) {
        javax.swing.JWindow toast = new javax.swing.JWindow(frame);
        JLabel label = new JLabel(message, JLabel.CENTER);
        label.setOpaque(true);
        label.setBackground(new java.awt.Color(60, 60, 60, 230));
        label.setForeground(java.awt.Color.WHITE);
        label.setBorder(new EmptyBorder(8, 18, 8, 18));
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 13f));
        toast.setContentPane(label);
        toast.pack();
        int x = frame.getX() + (frame.getWidth() - toast.getWidth()) / 2;
        int y = frame.getY() + frame.getHeight() - toast.getHeight() - 40;
        toast.setLocation(x, y);
        toast.setAlwaysOnTop(true);
        toast.setVisible(true);
        java.util.Timer t = new java.util.Timer(true);
        t.schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(toast::dispose);
            }
        }, 1800);
    }

    /** 仓库/应用设置页（独立页面，非对话框）。 */
    private JPanel buildSettingsPanel() {
        JPanel box = new UiTheme.PaperPanel(new BorderLayout());
        // 设置存仓库（加密），未解锁时无法读写
        if (sanctum == null || !sanctum.isUnlocked()) {
            box.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
            JLabel hint = new JLabel("请先打开一个仓库再配置仓库设置", JLabel.CENTER);
            hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 13f));
            box.add(hint, BorderLayout.CENTER);
            JButton backBtn = new JButton("返回");
            backBtn.addActionListener(e -> backFromSettings());
            JPanel p = new JPanel(new FlowLayout());
            p.setOpaque(false);
            p.add(backBtn);
            box.add(p, BorderLayout.SOUTH);
            return box;
        }
        com.flora.sanctum.model.LibraryConfig lc = sanctum.config();

        // 顶部按钮栏（模仿主界面工具栏：略深底 + 分割线），图标按钮"确定 / 返回"
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        topBar.setOpaque(true);
        topBar.setBackground(UiTheme.PAPER_LIGHT);
        JButton okBtn = iconButton(SvgIcon.get("ui/check", 29), "保存设置并返回");
        JButton backBtn = iconButton(SvgIcon.get("ui/close", 29), "返回");
        backBtn.addActionListener(e -> backFromSettings());
        topBar.add(okBtn);
        topBar.add(backBtn);
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(topBar, BorderLayout.NORTH);
        JPanel hLine = new JPanel();
        hLine.setOpaque(true);
        hLine.setBackground(UiTheme.DIVIDER);
        hLine.setPreferredSize(new Dimension(0, 1));
        hLine.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        header.add(hLine, BorderLayout.SOUTH);
        box.add(header, BorderLayout.NORTH);

        // 左栏：root 列表（设置 / 图标 / SSH 密钥 / 远程）
        settingsTree = new JTree();
        settingsTree.setRootVisible(false);
        settingsTree.setRowHeight(36);
        settingsTree.setCellRenderer(new SettingsTreeRenderer());
        DefaultMutableTreeNode top = new DefaultMutableTreeNode("设置");
        DefaultMutableTreeNode setNode = new DefaultMutableTreeNode("设置");
        setNode.setUserObject(ViewNodeType.SETTINGS);
        DefaultMutableTreeNode iconNode = new DefaultMutableTreeNode("图标");
        iconNode.setUserObject(ViewNodeType.ICON);
        DefaultMutableTreeNode sshNode = new DefaultMutableTreeNode("SSH 密钥");
        sshNode.setUserObject(ViewNodeType.SSH_KEY);
        DefaultMutableTreeNode remoteNode = new DefaultMutableTreeNode("远程");
        remoteNode.setUserObject(ViewNodeType.REMOTE);
        top.add(setNode);
        top.add(iconNode);
        top.add(sshNode);
        top.add(remoteNode);
        settingsTree.setModel(new DefaultTreeModel(top));
        settingsTree.addTreeSelectionListener(e -> refreshSettingsEntries());
        JPanel left = new JPanel(new BorderLayout());
        left.setOpaque(true);
        left.setBackground(UiTheme.PAPER_LIGHT);
        left.add(settingsTree, BorderLayout.CENTER);

        // 中栏：条目列表（选中 root 后显示其条目）
        settingsEntryModel = new DefaultListModel<>();
        settingsEntryList = new JList<>(settingsEntryModel);
        settingsEntryList.setFixedCellHeight(40);
        settingsEntryList.setOpaque(false);
        settingsEntryList.setBorder(new EmptyBorder(8, 10, 8, 10));
        settingsEntryList.setCellRenderer(new SettingsEntryRenderer());
        settingsEntryList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showSettingsSelection();
            }
        });
        JScrollPane entryScroll = new JScrollPane(settingsEntryList);
        entryScroll.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        entryScroll.setOpaque(false);
        entryScroll.getViewport().setOpaque(false);

        // 右栏：编辑面板（白色圆角悬浮卡片）
        settingsEditPanel = new JPanel();
        settingsEditPanel.setLayout(new BoxLayout(settingsEditPanel, BoxLayout.Y_AXIS));
        settingsEditPanel.setBorder(new EmptyBorder(8, 10, 8, 10));
        settingsEditPanel.setOpaque(false);
        JScrollPane editScroll = new JScrollPane(settingsEditPanel);
        editScroll.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        editScroll.setOpaque(false);
        editScroll.getViewport().setOpaque(false);
        CardPanel editCard = new CardPanel(new BorderLayout(), 6);
        editCard.add(editScroll, BorderLayout.CENTER);

        // 三栏：左 root + 中条目 + 右编辑（右分割线不显示，比例存储保留）
        JSplitPane rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, entryScroll, editCard);
        rightSplit.setDividerSize(0);
        rightSplit.setDividerLocation(240);
        keepDividerRatio(rightSplit, "ui.divider.right", 240);
        rightSplit.setOpaque(false);
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, rightSplit);
        mainSplit.setDividerLocation(160);
        keepDividerRatio(mainSplit, "ui.divider.main", 160);
        mainSplit.setOpaque(false);
        box.add(mainSplit, BorderLayout.CENTER);

        // 默认选中"设置" root
        settingsTree.setSelectionPath(new javax.swing.tree.TreePath(new Object[]{top, setNode}));

        okBtn.addActionListener(e -> {
            saveSettingsItems();
            showToast("设置已保存");
            backFromSettings();
        });
        return box;
    }

    /** 设置页中栏刷新：按左栏选中的 root 列出条目。 */
    private void refreshSettingsEntries() {
        settingsEntryModel.clear();
        settingsEntryIds.clear();
        settingsEntryKeys.clear();
        settingsEntryKinds.clear();
        Object sel = settingsTree.getLastSelectedPathComponent();
        if (!(sel instanceof DefaultMutableTreeNode node)) {
            return;
        }
        Object uo = node.getUserObject();
        if (uo == ViewNodeType.SETTINGS) {
            addSettingsEntry("主题", "theme");
            addSettingsEntry("自动锁定", "lock");
            addSettingsEntry("剪贴板清空", "clip");
            addSettingsEntry("独立运行", "standalone");
            settingsEntryList.setSelectedIndex(0);
        } else if (uo instanceof ViewNodeType tag) {
            switch (tag) {
                case ICON -> {
                    // 内置图标（不可删除）
                    for (String name : SvgIcon.libraryIcons()) {
                        settingsEntryModel.addElement("内置 · " + name);
                        settingsEntryIds.add(builtinIconId(name));
                        settingsEntryKinds.add(SettingsKind.ICON);
                    }
                    // 用户导入图标
                    for (IconNode icon : sanctum.iconTree().icons()) {
                        settingsEntryModel.addElement(iconLabel(icon));
                        settingsEntryIds.add(icon.uuid().toString());
                        settingsEntryKinds.add(SettingsKind.ICON);
                    }
                }
                case SSH_KEY -> {
                    for (SshKeyNode key : sanctum.sshKeyTree().keys()) {
                        settingsEntryModel.addElement(key.name());
                        settingsEntryIds.add(key.uuid().toString());
                        settingsEntryKinds.add(SettingsKind.SSH_KEY);
                    }
                }
                case REMOTE -> {
                    for (RemoteNode r : sanctum.remoteTree().remotes()) {
                        settingsEntryModel.addElement(r.name());
                        settingsEntryIds.add(r.uuid().toString());
                        settingsEntryKinds.add(SettingsKind.REMOTE);
                    }
                }
                default -> {
                }
            }
            settingsEntryList.setSelectedIndex(settingsEntryModel.size() > 0 ? 0 : -1);
        }
    }

    private void addSettingsEntry(String name, String key) {
        settingsEntryModel.addElement(name);
        settingsEntryKeys.add(key);
        settingsEntryKinds.add(SettingsKind.SETTING);
        settingsEntryIds.add(null);
    }

    /** 设置页右栏渲染：按中栏选中条目显示编辑内容。 */
    private void showSettingsSelection() {
        int idx = settingsEntryList.getSelectedIndex();
        settingsEditPanel.removeAll();
        if (idx >= 0 && idx < settingsEntryKinds.size()) {
            SettingsKind kind = settingsEntryKinds.get(idx);
            String id = settingsEntryIds.get(idx);
            switch (kind) {
                case SETTING -> renderSettingsItem(settingsEntryKeys.get(idx));
                case ICON -> {
                    renderSettingsIcon(id, settingsEditPanel);
                    addSettingsActionBtn("导入图片", this::doImportImageAndRefresh);
                    addSettingsActionBtn("删除图标", () -> {
                        if (isBuiltinIcon(id)) {
                            JOptionPane.showMessageDialog(frame, "预制图标不能删除", "提示",
                                    JOptionPane.INFORMATION_MESSAGE);
                            return;
                        }
                        try {
                            IconNode node = sanctum.iconTree().find(UUID.fromString(id));
                            if (node != null) {
                                node.delete();
                                refreshSettingsEntries();
                                statusLabel.setText("已删除图标");
                            }
                        } catch (Exception ex) {
                            statusLabel.setText("删除失败");
                        }
                    });
                }
                case SSH_KEY -> {
                    renderSshKeyPanel(UUID.fromString(id), settingsEditPanel);
                    addSettingsActionBtn("添加 SSH 密钥", this::addSshKeyAndRefresh);
                }
                case REMOTE -> {
                    renderRemotePanel(UUID.fromString(id), settingsEditPanel);
                    addSettingsActionBtn("添加远程", this::addRemoteAndRefresh);
                }
            }
        }
        settingsEditPanel.revalidate();
        settingsEditPanel.repaint();
    }

    /** 渲染单个设置项（主题 / 自动锁定 / 剪贴板清空）的编辑控件。 */
    private void renderSettingsItem(String key) {
        com.flora.sanctum.model.LibraryConfig lc = sanctum.config();
        if ("theme".equals(key)) {
            settingsThemeCombo = new javax.swing.JComboBox<>(new String[]{"system", "light", "dark"});
            settingsThemeCombo.setSelectedItem(lc.theme());
            settingsThemeCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            settingsThemeCombo.setAlignmentX(0f);
            settingsEditPanel.add(new JLabel("界面主题"));
            settingsEditPanel.add(javax.swing.Box.createVerticalStrut(4));
            settingsEditPanel.add(settingsThemeCombo);
        } else if ("lock".equals(key)) {
            settingsLockField = new JTextField(String.valueOf(lc.lockTimeoutSeconds()));
            settingsLockField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            settingsLockField.setAlignmentX(0f);
            settingsEditPanel.add(new JLabel("自动锁定（秒）"));
            settingsEditPanel.add(javax.swing.Box.createVerticalStrut(4));
            settingsEditPanel.add(settingsLockField);
        } else if ("clip".equals(key)) {
            settingsClipField = new JTextField(String.valueOf(lc.clipboardClearSeconds()));
            settingsClipField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            settingsClipField.setAlignmentX(0f);
            settingsEditPanel.add(new JLabel("剪贴板清空（秒）"));
            settingsEditPanel.add(javax.swing.Box.createVerticalStrut(4));
            settingsEditPanel.add(settingsClipField);
        } else if ("standalone".equals(key)) {
            renderStandaloneItem();
        }
    }

    /** 设置页"独立运行"项：展示当前形态并提供 配置/删除 独立运行操作。 */
    private void renderStandaloneItem() {
        Path root = currentRepoRoot();
        boolean isStandalone = root != null
                && java.nio.file.Files.isRegularFile(
                        root.resolve(com.flora.sanctum.app.bootstrap.VaultDetector.standaloneFileName()));
        settingsEditPanel.add(new JLabel("运行形态"));
        settingsEditPanel.add(javax.swing.Box.createVerticalStrut(4));
        JLabel state = new JLabel(isStandalone ? "当前：独立运行（自带 lib/ 与启动脚本）"
                : "当前：普通仓库（依赖本应用打开）");
        state.setAlignmentX(0f);
        settingsEditPanel.add(state);
        if (isStandalone) {
            addSettingsActionBtn("删除独立运行", this::downgradeStandalone);
        } else {
            addSettingsActionBtn("配置独立运行", this::upgradeStandalone);
        }
    }

    /** 把当前普通仓库升级为独立仓库（新增 standalone.json / lib/ / 启动脚本，数据不动）。 */
    private void upgradeStandalone() {
        Path root = currentRepoRoot();
        if (root == null) {
            showToast("请先打开仓库");
            return;
        }
        if (JOptionPane.showConfirmDialog(frame,
                "将当前普通仓库配置为独立运行？\n仓库根将新增 standalone.json、lib/ 与启动脚本（数据不动）。\n之后可用仓库内脚本独立启动。",
                "配置独立运行", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            com.flora.sanctum.app.bootstrap.RepoCreator.upgradeToStandalone(root, configForStandalone());
            refreshSettingsEntries();
            showToast("已配置独立运行");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "配置失败：" + ex.getMessage(), "错误",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /** 把当前独立仓库降级为普通仓库（移除 standalone.json / lib/ / 启动脚本，数据不动）。 */
    private void downgradeStandalone() {
        Path root = currentRepoRoot();
        if (root == null) {
            showToast("请先打开仓库");
            return;
        }
        if (JOptionPane.showConfirmDialog(frame,
                "删除独立运行？\n将移除 standalone.json、lib/ 与启动脚本，仓库恢复为普通仓库（数据不动）。",
                "删除独立运行", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            com.flora.sanctum.app.bootstrap.RepoCreator.downgradeToNormal(root);
            refreshSettingsEntries();
            showToast("已删除独立运行");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "删除失败：" + ex.getMessage(), "错误",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /** 当前仓库根（数据根 = 仓库根，普通/独立布局一致）。 */
    private Path currentRepoRoot() {
        if (unlockedVaultPath != null) {
            return Path.of(unlockedVaultPath);
        }
        return targetVaultRoot;
    }

    /** 复制给 standalone.json 的配置：优先当前仓库 LibraryConfig，否则应用级配置。 */
    private JsonObject configForStandalone() {
        if (sanctum != null && sanctum.isUnlocked()) {
            com.flora.sanctum.model.LibraryConfig lc = sanctum.config();
            JsonObject cfg = new JsonObject();
            cfg.put("theme", lc.theme());
            cfg.put("lockTimeoutSeconds", lc.lockTimeoutSeconds());
            cfg.put("clipboardClearSeconds", lc.clipboardClearSeconds());
            return cfg;
        }
        return loadAppConfig();
    }

    /** 保存已编辑的设置项到仓库。 */
    private void saveSettingsItems() {
        com.flora.sanctum.model.LibraryConfig lc = sanctum.config();
        if (settingsThemeCombo != null) {
            lc.setTheme((String) settingsThemeCombo.getSelectedItem());
        }
        if (settingsLockField != null) {
            try {
                lc.setLockTimeoutSeconds(Integer.parseInt(settingsLockField.getText()));
            } catch (NumberFormatException ignore) {
            }
        }
        if (settingsClipField != null) {
            try {
                lc.setClipboardClearSeconds(Integer.parseInt(settingsClipField.getText()));
            } catch (NumberFormatException ignore) {
            }
        }
        applyTheme(lc.theme());
        settingsThemeCombo = null;
        settingsLockField = null;
        settingsClipField = null;
    }

    /** 在设置右栏追加一个操作按钮。 */
    private void addSettingsActionBtn(String label, Runnable action) {
        JButton b = new JButton(label);
        b.setAlignmentX(0f);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        b.addActionListener(e -> action.run());
        settingsEditPanel.add(javax.swing.Box.createVerticalStrut(10));
        settingsEditPanel.add(b);
    }

    private void doImportImageAndRefresh() {
        doImportImage();
        refreshSettingsEntries();
    }

    private void addSshKeyAndRefresh() {
        addSshKey();
        refreshSettingsEntries();
    }

    private void addRemoteAndRefresh() {
        addRemote();
        refreshSettingsEntries();
    }

    /** 从设置页返回：已解锁回编辑页，否则回历史页（独立形态回解锁页）。 */
    private void backFromSettings() {
        if (sanctum != null && sanctum.isUnlocked()) {
            showEditPage();
        } else if (standalone) {
            showUnlockPage(targetVaultRoot);
        } else {
            showHistoryPage();
        }
    }

    /** 组树渲染器：按 userObject 类型渲染文本（ViewNodeType 区段→区段展示名；UUID→group name；其它 fallback）。 */
    private final class FolderTreeRenderer extends javax.swing.tree.DefaultTreeCellRenderer {
        @Override
        public java.awt.Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                               boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            setIcon(SvgIcon.get("ui/folder", 24));
            setDisabledIcon(SvgIcon.get("ui/folder", 24));
            if (value instanceof javax.swing.tree.DefaultMutableTreeNode node) {
                Object uo = node.getUserObject();
                if (uo instanceof ViewNodeType tag) {
                    if (tag == ViewNodeType.TRASH) {
                        setText("垃圾桶");
                        setIcon(SvgIcon.get("ui/trash", 24));
                        setDisabledIcon(SvgIcon.get("ui/trash", 24));
                    } else {
                        setText(sectionDisplayName(tag));
                    }
                } else if (uo instanceof TrashCategory cat) {
                    setText(cat.label);
                    setIcon(SvgIcon.get("ui/trash", 24));
                    setDisabledIcon(SvgIcon.get("ui/trash", 24));
                } else if (uo instanceof UUID uuid) {
                    String[] info = groupsById().get(uuid);
                    String name = info == null ? null : info[1];
                    if (name == null || name.isBlank()) {
                        // 垃圾桶中的非组节点（条目等）直接取节点已存文本
                        name = nodeName(uuid);
                    }
                    setText(name == null || name.isBlank() ? "未命名" : name);
                    // 文件夹设置了图标则优先显示
                    String iconId = groupIconOf(uuid);
                    if (iconId != null) {
                        Icon ic = iconById(iconId, 24);
                        if (ic != null) {
                            setIcon(ic);
                            setDisabledIcon(ic);
                        }
                    }
                }
            }
            return this;
        }
    }

    /** 条目列表渲染器：文件夹图标 + 锁图标，按列表项类型区分。 */
    /** 设置页中栏条目种类。 */
    private enum SettingsKind { SETTING, ICON, SSH_KEY, REMOTE }

    /** 设置页左栏渲染器：root 显示名（设置/图标/SSH 密钥/远程），无字符图标。 */
    private static final class SettingsTreeRenderer extends javax.swing.tree.DefaultTreeCellRenderer {
        @Override
        public java.awt.Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                               boolean expanded, boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            setIcon(null);
            setText(rootName(value));
            return this;
        }

        private static String rootName(Object value) {
            if (value instanceof javax.swing.tree.DefaultMutableTreeNode node) {
                Object uo = node.getUserObject();
                if (uo instanceof ViewNodeType tag) {
                    return switch (tag) {
                        case ICON -> "图标";
                        case SSH_KEY -> "SSH 密钥";
                        case REMOTE -> "远程";
                        case PASSWORD -> "密码库";
                        default -> "设置";
                    };
                }
                if (uo == ViewNodeType.SETTINGS) {
                    return "设置";
                }
            }
            return "?";
        }
    }

    /** 设置页中栏条目渲染器：纯文本 + 内边距（无字符图标）。 */
    private static final class SettingsEntryRenderer extends javax.swing.DefaultListCellRenderer {
        @Override
        public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                               boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            setIcon(null);
            setBorder(new EmptyBorder(6, 8, 6, 8));
            return this;
        }
    }

    private final class EntryListRenderer extends javax.swing.DefaultListCellRenderer {
        @Override
        public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                               boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            StoredNodeType type = index >= 0 && index < listItemTypes.size() ? listItemTypes.get(index) : null;
            // 条目/文件夹设置了图标则优先显示，否则默认 folder/entry
            String iconId = index >= 0 && index < listItemIcons.size() ? listItemIcons.get(index) : null;
            Icon custom = iconById(iconId, 24);
            if (custom != null) {
                setIcon(custom);
            } else {
                setIcon(type == StoredNodeType.GROUP ? SvgIcon.get("ui/folder", 24) : SvgIcon.get("ui/entry", 24));
            }
            return this;
        }
    }

    /** 圆角悬浮卡片：纯色背景（比中间底色更白）四周缩进露出底色 + 内容内边距。 */
    private static final class CardPanel extends JPanel {
        private final int arc = 16;
        private final int inset = 8; // 卡片背景四周缩进，露出底层形成悬浮感

        CardPanel(java.awt.LayoutManager layout, int pad) {
            super(layout);
            setOpaque(false);
            setBorder(new EmptyBorder(pad + inset, pad + inset, pad + inset, pad + inset));
        }

        @Override
        protected void paintComponent(Graphics g) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(UiTheme.CARD);
            g2.fill(new java.awt.geom.RoundRectangle2D.Float(inset, inset,
                    getWidth() - 1 - 2 * inset, getHeight() - 1 - 2 * inset, arc, arc));
            g2.dispose();
            // 不调用 super：opaque=false 且自身已画圆角背景
        }
    }

    /** 解锁按钮 JLayer UI：在按钮右内侧绘制转圈（覆盖，不占排版，按钮保持全宽居中）。 */
    private static final class UnlockSpinnerUI extends javax.swing.plaf.LayerUI<JButton> {
        private final SpinnerIcon spinner;

        UnlockSpinnerUI(SpinnerIcon spinner) {
            this.spinner = spinner;
        }

        @Override
        public void paint(Graphics g, javax.swing.JComponent c) {
            super.paint(g, c);
            if (Boolean.TRUE.equals(c.getClientProperty("spinner.visible"))) {
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                int size = spinner.getIconWidth();
                int x = c.getWidth() - size - 14;
                int y = (c.getHeight() - size) / 2;
                spinner.paintIcon(c, g2, x, y);
                g2.dispose();
            }
        }
    }

    /** 转圈图标：旋转的圆弧（Timer 更新 angle 驱动动画）。 */
    private static final class SpinnerIcon implements javax.swing.Icon {
        private final int size;
        private double angle;

        SpinnerIcon(int size) {
            this.size = size;
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }

        @Override
        public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            int inset = 2;
            g2.setColor(UiTheme.INK_MUTED);
            g2.setStroke(new java.awt.BasicStroke(2.5f, java.awt.BasicStroke.CAP_ROUND,
                    java.awt.BasicStroke.JOIN_ROUND));
            g2.drawArc(x + inset, y + inset, size - 2 * inset, size - 2 * inset,
                    (int) Math.round(angle), 300);
            g2.dispose();
        }
    }

    /**
     * 圆形字母图标：彩色圆底 + 名称首字母（最多 2 个）。
     * 底色按 HSL 生成：名称 hash → 色相（0..360°），固定饱和度/亮度 → 颜色连续多样、明暗统一且确定。
     */
    private static final class LetterIcon implements javax.swing.Icon {
        private static final float SATURATION = 0.45f;
        private static final float BRIGHTNESS = 0.60f;
        private final String text;
        private final java.awt.Color bg;
        private final int size;

        LetterIcon(String name) {
            this(name, 40);
        }

        LetterIcon(String name, int size) {
            this.size = size;
            String base = name == null || name.isBlank() ? "?" : name.trim();
            this.text = iconText(base);
            float hue = (Math.abs(base.hashCode()) % 360) / 360f;
            this.bg = java.awt.Color.getHSBColor(hue, SATURATION, BRIGHTNESS);
        }

        /**
         * 图标字符：先按分隔符（空格/连字符/下划线）与驼峰拆分为单词，
         * 若得 ≥2 个单词取前两个单词首字母；否则（单单词/无单词模式）取整个字符串首字符。
         */
        static String iconText(String s) {
            if (s == null || s.isBlank()) {
                return "?";
            }
            String trimmed = s.trim();
            java.util.List<String> words = new java.util.ArrayList<>();
            for (String part : trimmed.split("[\\s\\-_]+")) {
                if (part.isEmpty()) {
                    continue;
                }
                words.addAll(camelSplit(part));
            }
            if (words.size() >= 2) {
                return ("" + words.get(0).charAt(0) + words.get(1).charAt(0)).toUpperCase();
            }
            if (words.size() == 1) {
                return words.get(0).substring(0, 1).toUpperCase();
            }
            return trimmed.substring(0, 1).toUpperCase();
        }

        /** 驼峰拆分：小写→大写边界，以及"HTTPServer"式 大写→大写+小写 边界。 */
        static java.util.List<String> camelSplit(String s) {
            java.util.List<String> out = new java.util.ArrayList<>();
            StringBuilder cur = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (i > 0) {
                    char prev = s.charAt(i - 1);
                    boolean boundary = (Character.isLowerCase(prev) && Character.isUpperCase(c))
                            || (Character.isUpperCase(prev) && Character.isUpperCase(c)
                            && i + 1 < s.length() && Character.isLowerCase(s.charAt(i + 1)));
                    if (boundary) {
                        out.add(cur.toString());
                        cur = new StringBuilder();
                    }
                }
                cur.append(c);
            }
            if (cur.length() > 0) {
                out.add(cur.toString());
            }
            return out;
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }

        @Override
        public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y) {
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bg);
            g2.fillOval(x, y, size, size);
            g2.setColor(java.awt.Color.WHITE);
            java.awt.Font font = new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, size * 2 / 3);
            g2.setFont(font);
            java.awt.FontMetrics fm = g2.getFontMetrics();
            int tw = fm.stringWidth(text);
            int tx = x + (size - tw) / 2;
            int ty = y + (size - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(text, tx, ty);
            g2.dispose();
        }
    }
}
