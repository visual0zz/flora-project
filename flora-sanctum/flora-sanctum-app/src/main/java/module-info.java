/**
 * flora-sanctum-app 模块定义文件。
 * <p>
 * 应用入口（单一可执行 jar）：无参数启动 JavaFX GUI，有参数处理命令行。
 * 依赖 core（业务）与 JavaFX（界面）。
 * 本模块使用 AGPL-3.0 许可证（见 flora-sanctum/LICENSE）。
 */
module com.flora.sanctum.app {
    exports com.flora.sanctum.app;
    exports com.flora.sanctum.app.command;

    requires com.flora.sanctum.core;
    requires com.flora.shell;
    requires com.flora.root;
    requires javafx.controls;
    requires javafx.graphics;
    requires java.desktop;
}
