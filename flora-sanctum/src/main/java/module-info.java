/**
 * Flora Sanctum 密码管理工具模块。
 * <p>
 * 依赖 JavaFX（GUI）和 flora-root（JSON 序列化、工具类）。
 * JGit 通过源码 vendor 引入，不属于模块依赖。
 */
module com.flora.sanctum {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires com.flora.root;

    exports com.flora.sanctum;
    exports com.flora.sanctum.crypto;
    exports com.flora.sanctum.model;
    exports com.flora.sanctum.storage;

    opens com.flora.sanctum to javafx.fxml;
}
