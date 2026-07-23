package com.flora.sanctum.ui;

import com.flora.sanctum.model.Entry;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;

import java.util.List;

/**
 * 保险库条目列表视图（含搜索、分类过滤）。
 */
public class VaultListView extends BorderPane {

    private final ObservableList<Entry> entryList;
    private final FilteredList<Entry> filteredEntries;
    private final ListView<Entry> listView;
    private final TextField searchField;
    private final Label statusLabel;

    public VaultListView(Runnable onAdd, java.util.function.Consumer<Entry> onEdit,
                         Runnable onLock, Runnable onSync) {
        entryList = FXCollections.observableArrayList();
        filteredEntries = new FilteredList<>(entryList, e -> true);
        listView = new ListView<>(filteredEntries);

        setPadding(new Insets(10));
        getStyleClass().add("root");

        // Top: search bar + buttons
        HBox topBar = new HBox(8);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(0, 0, 10, 0));

        searchField = new TextField();
        searchField.setPromptText("搜索标题、用户名、URL...");
        searchField.getStyleClass().add("search-box");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        Button addButton = new Button("+ 新增");
        addButton.getStyleClass().add("button-primary");
        addButton.setOnAction(e -> onAdd.run());

        Button syncButton = new Button("同步");
        syncButton.setOnAction(e -> onSync.run());

        Button lockButton = new Button("锁定");
        lockButton.setOnAction(e -> onLock.run());

        topBar.getChildren().addAll(searchField, addButton, syncButton, lockButton);

        // Search filter
        searchField.textProperty().addListener((obs, o, n) -> {
            String filter = n == null ? "" : n.toLowerCase().trim();
            filteredEntries.setPredicate(entry -> {
                if (filter.isEmpty()) return true;
                return (entry.getTitle() != null && entry.getTitle().toLowerCase().contains(filter))
                        || (entry.getUsername() != null && entry.getUsername().toLowerCase().contains(filter))
                        || (entry.getUrl() != null && entry.getUrl().toLowerCase().contains(filter));
            });
        });

        // List view: custom cell factory
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Entry entry, boolean empty) {
                super.updateItem(entry, empty);
                if (empty || entry == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    VBox cellBox = new VBox(2);
                    cellBox.setPadding(new Insets(4, 0, 4, 0));

                    Label titleLabel = new Label(entry.getTitle() != null ? entry.getTitle() : "(无标题)");
                    titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #e0e0e0;");

                    String detail = (entry.getUsername() != null ? entry.getUsername() : "")
                            + (entry.getUrl() != null && !entry.getUrl().isEmpty() ? " · " + entry.getUrl() : "");
                    Label detailLabel = new Label(detail);
                    detailLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #9e9e9e;");

                    HBox tagRow = new HBox(6);
                    if (entry.getCategory() != null && !entry.getCategory().isEmpty()) {
                        Label catLabel = new Label(entry.getCategory());
                        catLabel.setStyle("-fx-background-color: #3a5a8a; -fx-text-fill: #e0e0e0; " +
                                "-fx-padding: 2px 8px; -fx-background-radius: 10px; -fx-font-size: 11px;");
                        tagRow.getChildren().add(catLabel);
                    }

                    cellBox.getChildren().addAll(titleLabel, detailLabel);
                    if (!tagRow.getChildren().isEmpty()) {
                        cellBox.getChildren().add(tagRow);
                    }
                    setGraphic(cellBox);
                }
            }
        });

        // Double-click to edit
        listView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                Entry selected = listView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    onEdit.accept(selected);
                }
            }
        });

        // Right-click context menu
        listView.setContextMenu(createContextMenu(onEdit));

        // Status label
        statusLabel = new Label();
        statusLabel.getStyleClass().add("label-subtitle");
        statusLabel.setPadding(new Insets(5, 0, 0, 0));

        setTop(topBar);
        setCenter(listView);
        setBottom(statusLabel);
    }

    private ContextMenu createContextMenu(java.util.function.Consumer<Entry> onEdit) {
        ContextMenu menu = new ContextMenu();

        MenuItem editItem = new MenuItem("编辑");
        editItem.setOnAction(e -> {
            Entry selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) onEdit.accept(selected);
        });

        MenuItem copyUserItem = new MenuItem("复制用户名");
        copyUserItem.setOnAction(e -> {
            Entry selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null && selected.getUsername() != null) {
                copyToClipboard(selected.getUsername());
            }
        });

        MenuItem copyPassItem = new MenuItem("复制密码");
        copyPassItem.setOnAction(e -> {
            Entry selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null && selected.getPassword() != null) {
                copyToClipboard(selected.getPassword());
            }
        });

        MenuItem deleteItem = new MenuItem("删除");
        deleteItem.setOnAction(e -> {
            Entry selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                        "删除 " + selected.getTitle() + "？", ButtonType.YES, ButtonType.NO);
                alert.showAndWait().ifPresent(r -> {
                    if (r == ButtonType.YES) {
                        entryList.remove(selected);
                    }
                });
            }
        });

        menu.getItems().addAll(editItem, copyUserItem, copyPassItem, deleteItem);
        return menu;
    }

    private void copyToClipboard(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }

    public void setEntries(List<Entry> entries) {
        entryList.setAll(entries);
        updateStatus();
    }

    public Entry getSelectedEntry() {
        return listView.getSelectionModel().getSelectedItem();
    }

    public void removeEntry(Entry entry) {
        entryList.remove(entry);
        updateStatus();
    }

    public void addOrUpdateEntry(Entry entry) {
        // Find and update, or add
        for (int i = 0; i < entryList.size(); i++) {
            if (entryList.get(i).getId().equals(entry.getId())) {
                entryList.set(i, entry);
                updateStatus();
                return;
            }
        }
        entryList.add(entry);
        updateStatus();
    }

    private void updateStatus() {
        statusLabel.setText("共 " + filteredEntries.size() + " 条记录");
    }
}
