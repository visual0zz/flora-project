package com.flora.sanctum;

import com.flora.sanctum.crypto.KeyDerivation;
import com.flora.sanctum.crypto.SecureRandomHolder;
import com.flora.sanctum.model.Entry;
import com.flora.sanctum.model.Vault;
import com.flora.sanctum.model.VaultMeta;
import com.flora.sanctum.storage.VaultStore;
import com.flora.sanctum.sync.CommitPush;
import com.flora.sanctum.sync.GitBackend;
import com.flora.sanctum.sync.GitException;
import com.flora.sanctum.sync.PullMerge;
import com.flora.sanctum.sync.RemoteConfig;
import com.flora.sanctum.ui.EntryEditor;
import com.flora.sanctum.ui.UnlockView;
import com.flora.sanctum.ui.VaultListView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextField;
import javafx.scene.control.ButtonBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Optional;

/**
 * Flora Sanctum 密码管理工具启动入口。
 */
public class Main extends Application {

    private static final Path VAULT_DIR = Paths.get(System.getProperty("user.home"), ".flora-sanctum", "vault");

    private Stage primaryStage;
    private Scene scene;
    private StackPane rootPane;

    private UnlockView unlockView;
    private VaultListView vaultListView;
    private EntryEditor entryEditor;

    // 当前保险库状态
    private Vault currentVault;
    private byte[] currentEncKey;
    private byte[] currentMacKey;
    private byte[] currentSalt;
    private int currentIterations;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        primaryStage.setTitle("Flora Sanctum");
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(600);

        rootPane = new StackPane();
        rootPane.getStyleClass().add("root");

        scene = new Scene(rootPane, 1000, 700);
        String css = getClass().getResource("ui/styles.css").toExternalForm();
        scene.getStylesheets().add(css);

        showUnlockView();

        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // ======================== 视图切换 ========================

    private void showUnlockView() {
        unlockView = new UnlockView(this::unlockVault, this::createVault);
        rootPane.getChildren().setAll(unlockView);
        primaryStage.setWidth(450);
        primaryStage.setHeight(450);
    }

    private void showVaultListView() {
        vaultListView = new VaultListView(
                this::addEntry,
                this::editEntry,
                this::lockVault,
                this::syncVault
        );
        vaultListView.setEntries(currentVault.getEntries());
        rootPane.getChildren().setAll(vaultListView);
        primaryStage.setWidth(1000);
        primaryStage.setHeight(700);
    }

    private void showEntryEditor(Entry entry) {
        if (entryEditor == null) {
            entryEditor = new EntryEditor(this::saveEntry, this::hideEntryEditor);
        }
        entryEditor.setEntry(entry);
        rootPane.getChildren().setAll(entryEditor);
    }

    private void hideEntryEditor() {
        if (currentVault != null) {
            vaultListView.setEntries(currentVault.getEntries());
        }
        rootPane.getChildren().setAll(vaultListView);
    }

    // ======================== 保险库操作 ========================

    private void unlockVault() {
        String password = unlockView.getPassword();
        if (password == null || password.isEmpty()) {
            unlockView.showError("请输入主密码");
            return;
        }

        unlockView.setLoading(true);
        new Thread(() -> {
            try {
                // 检查 vault 是否存在
                if (!java.nio.file.Files.exists(VAULT_DIR.resolve("meta.json"))) {
                    Platform.runLater(() -> {
                        unlockView.setLoading(false);
                        unlockView.showError("未找到保险库，请先创建");
                    });
                    return;
                }

                VaultMeta meta = VaultStore.loadMeta(VAULT_DIR);
                byte[] salt = Base64.getDecoder().decode(meta.getSalt());
                int iterations = meta.getIterations();

                byte[] km = KeyDerivation.derive(password.toCharArray(), salt, iterations);
                byte[] encKey = KeyDerivation.encryptionKey(km);
                byte[] macKey = KeyDerivation.macKey(km);

                Vault vault = VaultStore.load(VAULT_DIR, encKey, macKey);

                Platform.runLater(() -> {
                    unlockView.setLoading(false);
                    unlockView.clearPassword();
                    currentVault = vault;
                    currentEncKey = encKey;
                    currentMacKey = macKey;
                    currentSalt = salt;
                    currentIterations = iterations;
                    showVaultListView();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    unlockView.setLoading(false);
                    unlockView.showError("解锁失败：" + e.getMessage());
                });
            }
        }).start();
    }

    private void createVault() {
        String password = unlockView.getPassword();
        if (password == null || password.isEmpty()) {
            unlockView.showError("请输入主密码");
            return;
        }

        unlockView.setLoading(true);
        new Thread(() -> {
            try {
                byte[] salt = new byte[16];
                SecureRandomHolder.get().nextBytes(salt);
                int iterations = KeyDerivation.MIN_ITERATIONS;

                byte[] km = KeyDerivation.derive(password.toCharArray(), salt, iterations);
                byte[] encKey = KeyDerivation.encryptionKey(km);
                byte[] macKey = KeyDerivation.macKey(km);

                VaultMeta meta = new VaultMeta();
                meta.setSalt(Base64.getEncoder().encodeToString(salt));
                meta.setIterations(iterations);

                // 确保目录存在
                java.nio.file.Files.createDirectories(VAULT_DIR);
                VaultStore.create(VAULT_DIR, meta, macKey);

                // 初始化 Git
                try {
                    GitBackend.init(VAULT_DIR);
                    GitBackend.configureUser(VAULT_DIR, "Flora Sanctum", "sanctum@local");
                    CommitPush.addAndCommit(VAULT_DIR, "initialize vault");
                } catch (GitException ge) {
                    // Git 不是必需的，静默忽略
                }

                Platform.runLater(() -> {
                    unlockView.setLoading(false);
                    unlockView.clearPassword();
                    unlockView.showStatus("保险库创建成功！请输入密码解锁");

                    currentSalt = salt;
                    currentIterations = iterations;
                    currentEncKey = encKey;
                    currentMacKey = macKey;
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    unlockView.setLoading(false);
                    unlockView.showError("创建失败：" + e.getMessage());
                });
            }
        }).start();
    }

    private void lockVault() {
        // 清除密钥
        if (currentEncKey != null) {
            java.util.Arrays.fill(currentEncKey, (byte) 0);
        }
        if (currentMacKey != null) {
            java.util.Arrays.fill(currentMacKey, (byte) 0);
        }
        currentVault = null;
        currentEncKey = null;
        currentMacKey = null;
        currentSalt = null;
        showUnlockView();
    }

    // ======================== 条目操作 ========================

    private void addEntry() {
        showEntryEditor(null);
    }

    private void editEntry(Entry entry) {
        showEntryEditor(entry);
    }

    private void saveEntry() {
        Entry entry = entryEditor.getEntry();
        if (entry == null) {
            hideEntryEditor();
            return;
        }

        new Thread(() -> {
            try {
                currentVault.putEntry(entry);
                VaultStore.saveEntry(VAULT_DIR, entry, currentEncKey);

                // Git commit
                try {
                    CommitPush.addAndCommit(VAULT_DIR,
                            "update entry: " + entry.getTitle());
                } catch (GitException ignored) {
                }

                Platform.runLater(() -> {
                    vaultListView.addOrUpdateEntry(entry);
                    hideEntryEditor();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR, "保存失败：" + e.getMessage());
                    alert.showAndWait();
                });
            }
        }).start();
    }

    private void deleteSelectedEntry() {
        Entry selected = vaultListView.getSelectedEntry();
        if (selected == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "确定删除 " + selected.getTitle() + "？", ButtonType.YES, ButtonType.NO);
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.YES) {
            new Thread(() -> {
                try {
                    currentVault.removeEntry(selected.getId());
                    VaultStore.deleteEntry(VAULT_DIR, selected.getId());

                    try {
                        CommitPush.addAndCommit(VAULT_DIR,
                                "delete entry: " + selected.getTitle());
                    } catch (GitException ignored) {
                    }

                    Platform.runLater(() -> {
                        vaultListView.removeEntry(selected);
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.ERROR, "删除失败：" + e.getMessage());
                        alert.showAndWait();
                    });
                }
            }).start();
        }
    }

    // ======================== 同步操作 ========================

    private void syncVault() {
        Dialog<String[]> dialog = new Dialog<>();
        dialog.setTitle("同步设置");
        dialog.setHeaderText("配置远程 Git 仓库");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20));

        TextField urlField = new TextField();
        urlField.setPromptText("https://github.com/user/vault.git");

        TextField usernameField = new TextField();
        usernameField.setPromptText("用户名（可选）");

        TextField tokenField = new TextField();
        tokenField.setPromptText("Token / 密码（可选）");

        grid.add(new javafx.scene.control.Label("远程 URL:"), 0, 0);
        grid.add(urlField, 1, 0);
        GridPane.setHgrow(urlField, Priority.ALWAYS);
        grid.add(new javafx.scene.control.Label("用户名:"), 0, 1);
        grid.add(usernameField, 1, 1);
        grid.add(new javafx.scene.control.Label("Token:"), 0, 2);
        grid.add(tokenField, 1, 2);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Sync now button (separate from dialog)
        ButtonType syncNowType = new ButtonType("立即同步", ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().add(syncNowType);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                return new String[]{urlField.getText(), usernameField.getText(), tokenField.getText(), "save"};
            } else if (btn == syncNowType) {
                return new String[]{urlField.getText(), usernameField.getText(), tokenField.getText(), "sync"};
            }
            return null;
        });

        Optional<String[]> result = dialog.showAndWait();
        result.ifPresent(values -> {
            String url = values[0];
            String username = values[1];
            String token = values[2];
            String action = values[3];

            if (url == null || url.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.WARNING, "请输入远程仓库 URL");
                alert.showAndWait();
                return;
            }

            new Thread(() -> {
                try {
                    if ("save".equals(action)) {
                        RemoteConfig.addRemote(VAULT_DIR, "origin", url);
                        if (token != null && !token.isEmpty()) {
                            RemoteConfig.configureHttpCredentials(VAULT_DIR,
                                    username != null ? username : "", token);
                        }
                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.INFORMATION,
                                    "远程仓库配置已保存");
                            alert.showAndWait();
                        });
                    } else {
                        // Push
                        if (!GitBackend.isGitRepo(VAULT_DIR)) {
                            GitBackend.init(VAULT_DIR);
                        }
                        CommitPush.addAndCommit(VAULT_DIR, "sync before push");
                        CommitPush.push(VAULT_DIR);
                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.INFORMATION,
                                    "同步成功！");
                            alert.showAndWait();
                        });
                    }
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.ERROR,
                                "同步失败：" + e.getMessage());
                        alert.showAndWait();
                    });
                }
            }).start();
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
