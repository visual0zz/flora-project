/**
 * flora-sanctum-app 模块定义文件。
 * <p>
 * 应用入口（单一可执行 jar）：无参数启动 Swing GUI，有参数处理命令行。
 * 依赖 core（业务）、flora-shell（命令框架）与 java.desktop（Swing/AWT）。
 * 纯 JDK，无 JavaFX，可用 jlink 打包精简 JRE、免装 Java。
 * 本模块使用 AGPL-3.0 许可证（见 flora-sanctum/LICENSE）。
 */
module com.flora.sanctum.app {
    exports com.flora.sanctum.app;
    exports com.flora.sanctum.app.command;

    requires com.flora.sanctum.core;
    requires com.flora.shell;
    requires com.flora.root;
    requires java.desktop;
    requires java.logging;
    requires com.formdev.flatlaf;
    requires com.github.weisj.jsvg;
    requires com.sun.jna;
    requires com.sun.jna.platform;
}
