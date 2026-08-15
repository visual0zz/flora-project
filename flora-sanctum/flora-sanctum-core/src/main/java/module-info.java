/**
 * flora-sanctum-core 模块定义文件。
 * <p>
 * 密码管理器核心，纯 Java 无 UI 依赖。分层见各导出包注释。
 * 本模块使用 AGPL-3.0 许可证（见 flora-sanctum/LICENSE）。
 */
module com.flora.sanctum.core {
    // 加密、密钥派生、块信封（Bouncy Castle）——内部实现（impl）不导出
    exports com.flora.sanctum.crypto;
    // 纯字节存储引擎（不负责加密，Codec 由外部注入）——内部实现（impl）不导出
    exports com.flora.sanctum.store;
    // 适配器暴露的公开 API（条目/字段模型、解锁）
    exports com.flora.sanctum.model;
    // Git 同步（JGit 封装）
    exports com.flora.sanctum.sync;
    // 外部密钥服务 HTTP 传输（本地 localhost）
    exports com.flora.sanctum.server;
    // 用户配置目录（~/.flora-sanctum）
    exports com.flora.sanctum.config;

    requires org.bouncycastle.provider;
    requires org.eclipse.jgit;
    requires org.eclipse.jgit.ssh.apache;
    requires jdk.httpserver;
    requires com.flora.root;
}
