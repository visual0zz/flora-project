package com.flora.sanctum.app;

import com.flora.root.codec.json.model.JsonObject;
import com.flora.sanctum.config.UserConfig;
import com.flora.sanctum.model.Sanctum;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.UUID;

/**
 * flora-sanctum JavaFX GUI（无参数启动）。
 * <p>
 * 含：解锁屏（打开/新建 + 统一错误）、主界面三栏（组树/条目列表/编辑面板）、
 * 系统托盘、自动锁定、剪贴板定时清空、Material 明/暗主题、同步按钮、TOTP 倒计时、设置页。
 * UI 只调用 core 公开 API（见设计 07），不解密、不碰 Git。
 */
public final class SanctumGui extends Application {

    private final java.util.concurrent.atomic.AtomicReference<Sanctum> current =
            new java.util.concurrent.atomic.AtomicReference<>();
    private final UserConfig config = new UserConfig();
    private com.flora.sanctum.server.SanctumHttpServer httpServer;
    private Sanctum sanctum;
    private Stage stage;
    private java.util.Timer autoLockTimer;
    private java.util.Timer totpTimer;
    private java.util.Timer clipboardTimer;
    private UUID lastCopiedField;
    private String copiedPlaintext;
    private ListView<String> entryList;
    private TextField editField;
    private Label statusLabel;

    public static void launch(String[] args) {
        Application.launch(SanctumGui.class, args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        this.stage = stage;
        // 外部密钥服务 HTTP 跟随应用启动（始终监听；锁定时返回 locked）
        httpServer = new com.flora.sanctum.server.SanctumHttpServer(current::get, 0);
        httpServer.start();
        installTray();
        stage.setOnCloseRequest(e -> shutdown());
        stage.setTitle("flora-sanctum");
        stage.setScene(buildUnlockScene());
        stage.show();
    }

    /** 系统托盘（锁定/复制/退出，见设计 07）。 */
    private void installTray() {
        if (!java.awt.SystemTray.isSupported()) {
            return;
        }
        java.awt.TrayIcon icon = new java.awt.TrayIcon(
                java.awt.Toolkit.getDefaultToolkit().createImage(new byte[0]), "flora-sanctum");
        java.awt.PopupMenu menu = new java.awt.PopupMenu();
        java.awt.MenuItem lockItem = new java.awt.MenuItem("锁定");
        lockItem.addActionListener(e -> Platform.runLater(this::lock));
        java.awt.MenuItem copyItem = new java.awt.MenuItem("复制密码");
        copyItem.addActionListener(e -> Platform.runLater(() -> {
            if (copiedPlaintext != null) {
                ClipboardContent content = new ClipboardContent();
                content.putString(copiedPlaintext);
                Clipboard.getSystemClipboard().setContent(content);
            }
        }));
        java.awt.MenuItem exitItem = new java.awt.MenuItem("退出");
        exitItem.addActionListener(e -> Platform.runLater(this::shutdown));
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

    private void shutdown() {
        if (httpServer != null) {
            httpServer.stop();
        }
        stopTimers();
    }

    private void stopTimers() {
        if (autoLockTimer != null) {
            autoLockTimer.cancel();
            autoLockTimer = null;
        }
        if (totpTimer != null) {
            totpTimer.cancel();
            totpTimer = null;
        }
        if (clipboardTimer != null) {
            clipboardTimer.cancel();
            clipboardTimer = null;
        }
    }

    /** 应用 Material 主题（明/暗/跟随系统）。 */
    private void applyTheme() {
        String theme = config.theme();
        boolean dark = "dark".equals(theme)
                || ("system".equals(theme) && isSystemDark());
        Scene scene = stage.getScene();
        if (scene != null) {
            scene.getRoot().getStyleClass().removeAll("theme-light", "theme-dark");
            scene.getRoot().getStyleClass().add(dark ? "theme-dark" : "theme-light");
        }
    }

    private boolean isSystemDark() {
        // 探测 OS 明暗（macOS/Windows 简化）
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) {
            String apple = System.getProperty("apple.awt.application.appearance");
            return apple != null && apple.contains("dark");
        }
        return false;
    }

    private Scene newScene(javafx.scene.Parent root, double w, double h) {
        Scene s = new Scene(root, w, h);
        String css = SanctumGui.class.getResource("/material.css").toExternalForm();
        s.getStylesheets().add(css);
        applyTheme();
        return s;
    }

    /** 解锁屏（打开/新建 + 统一错误）。 */
    private Scene buildUnlockScene() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(20));
        Label title = new Label("flora-sanctum");

        Label dirLabel = new Label("未选择库目录");
        Button dirBtn = new Button("选择库目录…");
        dirBtn.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            var dir = chooser.showDialog(stage);
            if (dir != null) {
                dirLabel.setText(dir.getAbsolutePath());
            }
        });

        TextField pathField = new TextField();
        pathField.setPromptText("或直接输入库路径");
        PasswordField pwField = new PasswordField();
        pwField.setPromptText("主密码");
        Button unlockBtn = new Button("解锁");
        Label error = new Label();
        error.getStyleClass().add("error");

        // 回车提交
        pwField.setOnAction(e -> doUnlock(pathField, dirLabel, pwField, error));
        unlockBtn.setOnAction(e -> doUnlock(pathField, dirLabel, pwField, error));

        box.getChildren().addAll(title, dirLabel, dirBtn, pathField, pwField, unlockBtn, error);
        return newScene(box, 360, 260);
    }

    private void doUnlock(TextField pathField, Label dirLabel, PasswordField pwField, Label error) {
        String path = pathField.getText().trim();
        if (path.isEmpty()) {
            path = dirLabel.getText();
        }
        if (path.isEmpty() || "未选择库目录".equals(path)) {
            error.setText("请选择库目录");
            return;
        }
        char[] pw = pwField.getText().toCharArray();
        try {
            Path root = Path.of(path);
            boolean exists = java.nio.file.Files.isDirectory(root);
            if (exists) {
                sanctum = Sanctum.open(root);
            } else {
                sanctum = Sanctum.createAndUnlock(root, pw);
            }
            if (sanctum.isUnlocked()) {
                sanctum.unlock(pw);
            }
            current.set(sanctum);
            stage.setScene(buildMainScene());
            startAutoLockTimer();
        } catch (Exception ex) {
            // 统一提示"解锁失败"，不区分密码错/数据损坏（见设计 03）
            error.setText("解锁失败");
        } finally {
            java.util.Arrays.fill(pw, (char) 0);
        }
    }

    /** 自动锁定定时器（默认 5 分钟不活动，见设计 03）。 */
    private void startAutoLockTimer() {
        if (autoLockTimer != null) {
            autoLockTimer.cancel();
        }
        autoLockTimer = new java.util.Timer(true);
        autoLockTimer.schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                Platform.runLater(SanctumGui.this::lock);
            }
        }, config.lockTimeoutSeconds() * 1000L);
    }

    /** 活动时重置自动锁定。 */
    private void resetAutoLock() {
        if (sanctum != null && sanctum.isUnlocked()) {
            startAutoLockTimer();
        }
    }

    /** 锁定：丢弃 KEK/DEK、回解锁屏。 */
    private void lock() {
        if (sanctum != null) {
            sanctum.close();
        }
        current.set(null);
        stopTimers();
        stage.setScene(buildUnlockScene());
    }

    /** 主界面三栏布局。 */
    private Scene buildMainScene() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));
        root.setOnMouseClicked(e -> resetAutoLock());

        // 顶部工具栏
        Label title = new Label("flora-sanctum");
        Button syncBtn = new Button("同步");
        Button lockBtn = new Button("锁定");
        Button settingsBtn = new Button("设置");
        syncBtn.setVisible(isFullyManaged());
        statusLabel = new Label();
        HBox top = new HBox(8, title, syncBtn, settingsBtn, lockBtn, statusLabel);
        top.setPadding(new Insets(0, 0, 8, 0));
        root.setTop(top);

        // 左：组树
        TreeView<String> tree = new TreeView<>();
        TreeItem<String> rootItem = new TreeItem<>("全部");
        tree.setRoot(rootItem);
        rootItem.setExpanded(true);

        // 中：条目列表
        entryList = new ListView<>();
        refreshEntryList();

        // 右：编辑面板
        editField = new TextField();
        editField.setPromptText("字段值");
        Button copyBtn = new Button("复制");
        copyBtn.setOnAction(e -> copySelected());
        Label totpLabel = new Label();
        VBox editPanel = new VBox(8, new Label("编辑"), editField, copyBtn, new Label("TOTP"), totpLabel);
        editPanel.setPrefWidth(220);

        javafx.scene.control.SplitPane split = new javafx.scene.control.SplitPane(tree, entryList, editPanel);
        root.setCenter(split);

        syncBtn.setOnAction(e -> doSync());
        lockBtn.setOnAction(e -> lock());
        settingsBtn.setOnAction(e -> stage.setScene(buildSettingsScene()));
        entryList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            resetAutoLock();
            updateTotpDisplay(totpLabel);
        });

        return newScene(root, 900, 600);
    }

    /** 更新编辑面板的 TOTP 显示（当前条目下 kind:totp 字段的验证码 + 倒计时）。 */
    private void updateTotpDisplay(Label totpLabel) {
        String sel = entryList.getSelectionModel().getSelectedItem();
        if (sel == null || !sel.startsWith("field:")) {
            totpLabel.setText("");
            return;
        }
        String uuidStr = sel.substring(sel.lastIndexOf('[') + 1, sel.lastIndexOf(']'));
        try {
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

    private boolean isFullyManaged() {
        return sanctum != null
                && new com.flora.sanctum.sync.SyncService(sanctum.root()).isFullyManaged();
    }

    private void refreshEntryList() {
        entryList.getItems().clear();
        for (UUID u : sanctum.listObjectUuids()) {
            JsonObject n = sanctum.getEntry(u);
            if (n != null) {
                entryList.getItems().add(n.getString("type") + ": " + n.getString("name") + "  [" + u + "]");
            }
        }
    }

    private void copySelected() {
        String sel = entryList.getSelectionModel().getSelectedItem();
        if (sel == null) {
            return;
        }
        String value = editField.getText();
        if (value.isEmpty()) {
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(value);
        Clipboard.getSystemClipboard().setContent(content);
        lastCopiedField = UUID.randomUUID();
        copiedPlaintext = value;
        startClipboardTimer();
        resetAutoLock();
    }

    /** 剪贴板定时清空（默认 30s，见设计 03）。 */
    private void startClipboardTimer() {
        if (clipboardTimer != null) {
            clipboardTimer.cancel();
        }
        clipboardTimer = new java.util.Timer(true);
        clipboardTimer.schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    ClipboardContent empty = new ClipboardContent();
                    empty.putString("");
                    Clipboard.getSystemClipboard().setContent(empty);
                    copiedPlaintext = null;
                });
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
            refreshEntryList();
            statusLabel.setText("已同步");
        } catch (Exception e) {
            statusLabel.setText("同步失败");
        }
    }

    /** 设置页。 */
    private Scene buildSettingsScene() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(20));

        Text themeTitle = new Text("主题");
        javafx.scene.control.ChoiceBox<String> themeChoice = new javafx.scene.control.ChoiceBox<>();
        themeChoice.getItems().addAll("light", "dark", "system");
        themeChoice.setValue(config.theme());
        themeChoice.setOnAction(e -> {
            config.setTheme(themeChoice.getValue());
            applyTheme();
        });

        Text lockTitle = new Text("自动锁定（秒）");
        TextField lockField = new TextField(String.valueOf(config.lockTimeoutSeconds()));
        Button saveLock = new Button("保存");
        saveLock.setOnAction(e -> {
            try {
                config.setLockTimeoutSeconds(Integer.parseInt(lockField.getText()));
            } catch (NumberFormatException ignore) {
            }
        });

        Text clipTitle = new Text("剪贴板清空（秒）");
        TextField clipField = new TextField(String.valueOf(config.clipboardClearSeconds()));
        Button saveClip = new Button("保存");
        saveClip.setOnAction(e -> {
            try {
                config.setClipboardClearSeconds(Integer.parseInt(clipField.getText()));
            } catch (NumberFormatException ignore) {
            }
        });

        Button back = new Button("返回");
        back.setOnAction(e -> {
            if (sanctum != null && sanctum.isUnlocked()) {
                stage.setScene(buildMainScene());
            } else {
                stage.setScene(buildUnlockScene());
            }
        });

        box.getChildren().addAll(themeTitle, themeChoice, lockTitle, lockField, saveLock,
                clipTitle, clipField, saveClip, back);
        return newScene(box, 360, 420);
    }
}
