/**
 * flora-sanctum-core 模块定义文件。
 * <p>
 * 密码管理器核心，纯 Java 无 UI 依赖。分层见各导出包注释。
 * 本模块使用 AGPL-3.0 许可证（见 flora-sanctum/LICENSE）。
 */
module com.flora.sanctum.core {
    // 加密、密钥派生、块信封（Bouncy Castle）
    exports com.flora.sanctum.crypto;
    exports com.flora.sanctum.crypto.impl;
    // 纯字节存储引擎（不负责加密，Codec 由外部注入）
    exports com.flora.sanctum.store;
    exports com.flora.sanctum.store.impl;
    // 适配器暴露的公开 API（条目/字段模型、解锁）
    exports com.flora.sanctum.model;
    // Git 同步（JGit 封装）
    exports com.flora.sanctum.sync;
    // 外部密钥服务 HTTP 传输（本地 localhost）
    exports com.flora.sanctum.server;

    requires org.bouncycastle.provider;
    requires org.eclipse.jgit;
    requires jdk.httpserver;
}
