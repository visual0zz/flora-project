/**
 * Flora Sanctum 密码管理工具模块。
 * <p>
 * 依赖 JavaFX（GUI）和 flora-root（JSON 序列化、工具类）。
 * Git 同步通过 JDK ProcessBuilder 调用系统 git CLI，0 额外依赖。
 * <p>
 * 各导出包的定位见下方注释。
 */
module com.flora.sanctum {
    requires javafx.base;
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires com.flora.root;

    // 应用入口与公共类型
    exports com.flora.sanctum;
    // 密码学相关工具
    exports com.flora.sanctum.crypto;
    // 数据模型
    exports com.flora.sanctum.model;
    // 本地存储
    exports com.flora.sanctum.storage;
    // Git 同步
    exports com.flora.sanctum.sync;
    // JavaFX 界面
    exports com.flora.sanctum.ui;

    opens com.flora.sanctum to javafx.fxml;
    opens com.flora.sanctum.ui to javafx.fxml;
}
