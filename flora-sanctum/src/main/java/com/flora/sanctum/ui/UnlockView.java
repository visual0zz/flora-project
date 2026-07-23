package com.flora.sanctum.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;

/**
 * 解锁/创建保险库视图。
 */
public class UnlockView extends VBox {

    private final PasswordField passwordField;
    private final CheckBox showPasswordCheck;
    private final Label errorLabel;
    private final Label statusLabel;
    private final Button actionButton;
    private final Button toggleModeButton;
    private final TextField passwordVisibleField;
    private boolean createMode;

    public UnlockView(Runnable onUnlock, Runnable onCreate) {
        setAlignment(Pos.CENTER);
        setSpacing(14);
        setPadding(new Insets(40));
        getStyleClass().add("root");

        // Title
        Label title = new Label("Flora Sanctum");
        title.getStyleClass().add("label-title");

        Label subtitle = new Label("密码管理工具");
        subtitle.getStyleClass().add("label-subtitle");
        subtitle.setTextAlignment(TextAlignment.CENTER);

        // Password field
        passwordField = new PasswordField();
        passwordField.setPromptText("输入主密码");
        passwordField.setMaxWidth(320);

        passwordVisibleField = new TextField();
        passwordVisibleField.setPromptText("输入主密码");
        passwordVisibleField.setMaxWidth(320);
        passwordVisibleField.setVisible(false);
        passwordVisibleField.setManaged(false);

        // Sync bind password fields
        passwordField.textProperty().addListener((obs, o, n) ->
                passwordVisibleField.setText(n));
        passwordVisibleField.textProperty().addListener((obs, o, n) ->
                passwordField.setText(n));

        // Show password checkbox
        showPasswordCheck = new CheckBox("显示密码");
        showPasswordCheck.setTextFill(javafx.scene.paint.Color.web("#9e9e9e"));
        showPasswordCheck.selectedProperty().addListener((obs, o, n) -> {
            passwordField.setVisible(!n);
            passwordField.setManaged(!n);
            passwordVisibleField.setVisible(n);
            passwordVisibleField.setManaged(n);
        });

        // Error label
        errorLabel = new Label();
        errorLabel.getStyleClass().add("label-error");
        errorLabel.setVisible(false);

        // Status label
        statusLabel = new Label();
        statusLabel.getStyleClass().add("label-success");
        statusLabel.setVisible(false);

        // Action button
        actionButton = new Button("解锁");
        actionButton.getStyleClass().add("button-primary");
        actionButton.setMaxWidth(320);
        actionButton.setPrefWidth(320);

        // Toggle create/unlock mode
        toggleModeButton = new Button("创建新保险库");
        toggleModeButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #4a9eff; -fx-underline: true;");
        toggleModeButton.setOnAction(e -> toggleMode());

        actionButton.setOnAction(e -> {
            String password = getPassword();
            if (password.isEmpty()) {
                showError("请输入主密码");
                return;
            }
            hideError();
            if (createMode) {
                onCreate.run();
            } else {
                onUnlock.run();
            }
        });

        getChildren().addAll(title, subtitle, passwordField, passwordVisibleField,
                showPasswordCheck, errorLabel, statusLabel, actionButton, toggleModeButton);
    }

    private void toggleMode() {
        createMode = !createMode;
        if (createMode) {
            actionButton.setText("创建");
            toggleModeButton.setText("已有保险库？点击解锁");
        } else {
            actionButton.setText("解锁");
            toggleModeButton.setText("创建新保险库");
        }
        clearPassword();
        hideError();
        hideStatus();
    }

    public boolean isCreateMode() {
        return createMode;
    }

    public String getPassword() {
        return passwordVisibleField.isVisible()
                ? passwordVisibleField.getText()
                : passwordField.getText();
    }

    public void clearPassword() {
        passwordField.clear();
        passwordVisibleField.clear();
    }

    public void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
    }

    public void hideError() {
        errorLabel.setVisible(false);
    }

    public void showStatus(String msg) {
        statusLabel.setText(msg);
        statusLabel.setVisible(true);
    }

    public void hideStatus() {
        statusLabel.setVisible(false);
    }

    public void setLoading(boolean loading) {
        actionButton.setDisable(loading);
        actionButton.setText(loading ? "处理中..." : (createMode ? "创建" : "解锁"));
    }
}
