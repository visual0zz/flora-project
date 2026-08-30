/**
 * flora-sanctum-core 模块定义文件。
 * <p>
 * 密码管理器核心，纯 Java 无 UI 依赖，专注数据读写（加密/存储/数据模型与树）。
 * 同步（git/ssh）与 HTTP 外部密钥服务已迁至 app 模块。
 * 本模块使用 AGPL-3.0 许可证（见 flora-sanctum/LICENSE）。
 */
module com.flora.sanctum.core {
    // 加密、密钥派生、块信封（纯 JDK + 自研实现）——内部实现（impl）不导出
    exports com.flora.sanctum.core.crypto;
    // 纯字节存储引擎（不负责加密，Codec 由外部注入）——内部实现（impl）不导出
    exports com.flora.sanctum.core.store;
    // 数据模型与树（元数据/配置/数据树/节点）
    exports com.flora.sanctum.core.model;
    // 数据树与节点（对象树/图标/SSH/远程，节点承担操作）
    exports com.flora.sanctum.core.model.tree;
    // 解锁与密钥状态
    exports com.flora.sanctum.core.model.vault;
    // 用户配置目录（~/.flora-sanctum）
    exports com.flora.sanctum.core.config;
    // 数据格式服务（导入/导出：KDBX / Sanctum CSV·JSON）
    exports com.flora.sanctum.core.io.importer;
    exports com.flora.sanctum.core.io.exporter;
    // 内置图标库（数据层资源，供渲染与导入映射复用）
    exports com.flora.sanctum.core.icon;

    requires com.flora.root;
    requires java.xml;
}
