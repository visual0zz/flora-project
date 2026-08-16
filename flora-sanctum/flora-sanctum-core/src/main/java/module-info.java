/**
 * flora-sanctum-core 模块定义文件。
 * <p>
 * 密码管理器核心，纯 Java 无 UI 依赖，专注数据读写（加密/存储/数据模型与树）。
 * 同步（git/ssh）与 HTTP 外部密钥服务已迁至 app 模块。
 * 本模块使用 AGPL-3.0 许可证（见 flora-sanctum/LICENSE）。
 */
module com.flora.sanctum.core {
    // 加密、密钥派生、块信封（Bouncy Castle）——内部实现（impl）不导出
    exports com.flora.sanctum.crypto;
    // 纯字节存储引擎（不负责加密，Codec 由外部注入）——内部实现（impl）不导出
    exports com.flora.sanctum.store;
    // 数据模型与树（元数据/配置/数据树/节点）
    exports com.flora.sanctum.model;
    // 数据树与节点（对象树/图标/SSH/远程，节点承担操作）
    exports com.flora.sanctum.model.tree;
    // 解锁与密钥状态
    exports com.flora.sanctum.model.vault;
    // 用户配置目录（~/.flora-sanctum）
    exports com.flora.sanctum.config;

    requires org.bouncycastle.provider;
    requires com.flora.root;
}
