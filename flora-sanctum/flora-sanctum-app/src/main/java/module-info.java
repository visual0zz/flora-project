/**
 * flora-sanctum-app 模块定义文件。
 * <p>
 * 应用入口（单一可执行 jar）：Swing GUI 为唯一交互。启动时按仓库形态分流
 * （独立仓库直接解锁 / 应用形态进历史仓库列表页）。依赖 core（数据读写/模型）与
 * java.desktop（Swing/AWT）。同步（git/ssh）与 HTTP 外部密钥服务位于本模块
 * （app.sync / app.server）。本模块使用 AGPL-3.0 许可证（见 flora-sanctum/LICENSE）。
 */
module com.flora.sanctum.app {
    exports com.flora.sanctum.app;
    exports com.flora.sanctum.app.ui;
    exports com.flora.sanctum.app.sync;
    exports com.flora.sanctum.app.server;

    requires com.flora.sanctum.core;
    requires com.flora.root;
    requires java.desktop;
    requires java.logging;
    requires java.net.http;
    requires jdk.httpserver;
    requires com.formdev.flatlaf;
    requires com.github.weisj.jsvg;
}
