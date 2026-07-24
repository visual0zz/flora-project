package com.flora.sanctum.ui;

import com.flora.sanctum.crypto.SecureRandomHolder;
import com.flora.sanctum.model.Entry;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;

/**
 * 条目新增/编辑视图。
 */
public class EntryEditor extends VBox {

    private final TextField titleField;
    private final TextField usernameField;
    private final PasswordField passwordField;
    private final TextField urlField;
    private final TextField categoryField;
    private final TextArea noteArea;
    private final Button saveButton;
    private final Button cancelButton;
    private final Button generatePasswordButton;
    private final Button copyPasswordButton;
    private Entry editingEntry;

    private static final String CHARS_LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String CHARS_UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String DIGITS = "0123456789";
    private static final String SPECIAL = "!@#$%^&*()-_=+[]{}|;:,.<>?";

    public EntryEditor(Runnable onSave, Runnable onCancel) {
        setSpacing(10);
        setPadding(new Insets(20));
        getStyleClass().add("root");
        setPrefWidth(400);

        Label title = new Label("编辑条目");
        title.getStyleClass().add("label-title");
        title.setPadding(new Insets(0, 0, 10, 0));

        // Title
        titleField = new TextField();
        titleField.setPromptText("标题");

        // Username
        usernameField = new TextField();
        usernameField.setPromptText("用户名");

        // Password with generate & copy
        HBox passwordBox = new HBox(6);
        passwordBox.setAlignment(Pos.CENTER_LEFT);
        passwordField = new PasswordField();
        passwordField.setPromptText("密码");
        HBox.setHgrow(passwordField, Priority.ALWAYS);

        generatePasswordButton = new Button("生成");
        generatePasswordButton.getStyleClass().addAll("button", "button-small");
        generatePasswordButton.setOnAction(e -> generatePassword());

        copyPasswordButton = new Button("复制");
        copyPasswordButton.getStyleClass().addAll("button", "button-small");
        copyPasswordButton.setOnAction(e -> copyToClipboard(passwordField.getText()));

        passwordBox.getChildren().addAll(passwordField, generatePasswordButton, copyPasswordButton);

        // URL
        urlField = new TextField();
        urlField.setPromptText("URL（可选）");

        // Category
        categoryField = new TextField();
        categoryField.setPromptText("分类（可选）");

        // Note
        noteArea = new TextArea();
        noteArea.setPromptText("备注（可选）");
        noteArea.setPrefRowCount(4);

        // Buttons
        HBox buttonBox = new HBox(10);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));

        saveButton = new Button("保存");
        saveButton.getStyleClass().add("button-primary");
        saveButton.setOnAction(e -> {
            if (titleField.getText().isEmpty()) {
                showAlert("请输入标题");
                return;
            }
            if (editingEntry == null) {
                editingEntry = new Entry();
            }
            editingEntry.setTitle(titleField.getText());
            editingEntry.setUsername(usernameField.getText());
            editingEntry.setPassword(passwordField.getText());
            editingEntry.setUrl(urlField.getText());
            editingEntry.setCategory(categoryField.getText());
            editingEntry.setNote(noteArea.getText());
            editingEntry.touch();
            onSave.run();
        });

        cancelButton = new Button("取消");
        cancelButton.setOnAction(e -> onCancel.run());

        buttonBox.getChildren().addAll(saveButton, cancelButton);

        getChildren().addAll(title,
                label("标题 *"), titleField,
                label("用户名"), usernameField,
                label("密码"), passwordBox,
                label("URL"), urlField,
                label("分类"), categoryField,
                label("备注"), noteArea,
                buttonBox);
    }

    private static Label label(String text) {
        Label l = new Label(text);
        l.setPadding(new Insets(4, 0, 0, 0));
        return l;
    }

    public void setEntry(Entry entry) {
        this.editingEntry = entry;
        if (entry != null) {
            titleField.setText(entry.getTitle());
            usernameField.setText(entry.getUsername());
            passwordField.setText(entry.getPassword());
            urlField.setText(entry.getUrl());
            categoryField.setText(entry.getCategory());
            noteArea.setText(entry.getNote());
        } else {
            clear();
        }
    }

    public Entry getEntry() {
        return editingEntry;
    }

    public void clear() {
        editingEntry = null;
        titleField.clear();
        usernameField.clear();
        passwordField.clear();
        urlField.clear();
        categoryField.clear();
        noteArea.clear();
    }

    private void generatePassword() {
        String chars = CHARS_LOWER + CHARS_UPPER + DIGITS + SPECIAL;
        int length = 20;
        java.security.SecureRandom sr = SecureRandomHolder.get();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(sr.nextInt(chars.length())));
        }
        passwordField.setText(sb.toString());
    }

    private void copyToClipboard(String text) {
        if (text == null || text.isEmpty()) return;
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING, msg, ButtonType.OK);
        alert.showAndWait();
    }
}
