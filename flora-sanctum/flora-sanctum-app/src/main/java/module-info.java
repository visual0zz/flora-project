/**
 * flora-sanctum-app 模块定义文件。
 * <p>
 * 应用入口（单一可执行 jar）：无参数启动 Swing GUI，有参数处理命令行。
 * 依赖 core（数据读写/模型）、flora-shell（命令框架）与 java.desktop（Swing/AWT）。
 * 同步（git/ssh）与 HTTP 外部密钥服务位于本模块（app.sync / app.server）。
 * 本模块使用 AGPL-3.0 许可证（见 flora-sanctum/LICENSE）。
 */
module com.flora.sanctum.app {
    exports com.flora.sanctum.app;
    exports com.flora.sanctum.app.command;
    exports com.flora.sanctum.app.ui;
    exports com.flora.sanctum.app.sync;
    exports com.flora.sanctum.app.server;

    requires com.flora.sanctum.core;
    requires com.flora.shell;
    requires com.flora.root;
    requires java.desktop;
    requires java.logging;
    requires java.net.http;
    requires jdk.httpserver;
    requires org.eclipse.jgit;
    requires org.eclipse.jgit.ssh.apache;
    requires com.formdev.flatlaf;
    requires com.github.weisj.jsvg;
    requires com.sun.jna;
    requires com.sun.jna.platform;
}
