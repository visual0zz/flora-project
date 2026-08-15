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

    requires org.bouncycastle.provider;
}
