package com.flora.sanctum;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 * Flora Sanctum 密码管理工具启动入口。
 */
public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        Label label = new Label("Flora Sanctum");
        label.setStyle("-fx-font-size: 24px; -fx-text-fill: #e0e0e0;");

        StackPane root = new StackPane(label);
        root.setStyle("-fx-background-color: #1e1e1e;");

        Scene scene = new Scene(root, 400, 300);
        primaryStage.setTitle("Flora Sanctum");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
