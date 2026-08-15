package com.flora.sanctum.app;

import com.flora.sanctum.model.Sanctum;
import com.flora.root.codec.json.model.JsonObject;
import com.flora.root.codec.JsonUtil;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.UUID;

/**
 * flora-sanctum JavaFX GUI（无参数启动）。
 * <p>
 * 解锁屏：选择库目录 + 输入主密码。解锁后主界面显示条目列表。
 * UI 只调用 core 公开 API（见设计 07"UI 与 core 的边界"），不解密、不碰 Git。
 */
public final class SanctumGui extends Application {

    private Sanctum sanctum;

    public static void launch(String[] args) {
        Application.launch(SanctumGui.class, args);
    }

    @Override
    public void start(Stage stage) {
        stage.setTitle("flora-sanctum");
        stage.setScene(buildUnlockScene(stage));
        stage.show();
    }

    /** 解锁屏。 */
    private Scene buildUnlockScene(Stage stage) {
        VBox box = new VBox(10);
        box.setPadding(new Insets(20));

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

        unlockBtn.setOnAction(e -> {
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
                stage.setScene(buildMainScene(stage));
            } catch (Exception ex) {
                error.setText("解锁失败：" + ex.getMessage());
            } finally {
                java.util.Arrays.fill(pw, (char) 0);
            }
        });

        box.getChildren().addAll(dirLabel, dirBtn, pathField, pwField, unlockBtn, error);
        return new Scene(box, 360, 240);
    }

    /** 主界面：条目列表。 */
    private Scene buildMainScene(Stage stage) {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        Label title = new Label("条目（锁定：关闭窗口）");
        ListView<String> list = new ListView<>();
        for (UUID u : sanctum.store().list()) {
            JsonObject n = sanctum.getEntry(u);
            if (n != null) {
                list.getItems().add(n.getString("type") + ": " + n.getString("name") + "  [" + u + "]");
            }
        }
        Button lockBtn = new Button("锁定");
        lockBtn.setOnAction(e -> {
            sanctum.close();
            Platform.runLater(() -> stage.setScene(buildUnlockScene(stage)));
        });

        VBox top = new VBox(5, title, lockBtn);
        root.setTop(top);
        root.setCenter(list);
        return new Scene(root, 600, 400);
    }
}
