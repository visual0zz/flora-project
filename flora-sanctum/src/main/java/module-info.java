/**
 * Flora Sanctum 密码管理工具模块。
 * <p>
 * 依赖 JavaFX（GUI）和 flora-root（JSON 序列化、工具类）。
 * Git 同步通过 JDK ProcessBuilder 调用系统 git CLI，0 额外依赖。
 */
module com.flora.sanctum {
    requires javafx.base;
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires com.flora.root;

    exports com.flora.sanctum;
    exports com.flora.sanctum.crypto;
    exports com.flora.sanctum.model;
    exports com.flora.sanctum.storage;
    exports com.flora.sanctum.sync;
    exports com.flora.sanctum.ui;

    opens com.flora.sanctum to javafx.fxml;
    opens com.flora.sanctum.ui to javafx.fxml;
}
