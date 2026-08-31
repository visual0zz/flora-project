/**
 * flora-sanctum-vault-formats 模块：第三方密码仓库的只读导入。
 * <p>仅依赖 JDK、{@code flora-root}（密码学原语与 JSON 编解码）与 {@code flora-sanctum-kdbx}
 * （通用输出模型 {@code KdbxDocument}），不依赖 core 或任何 Sanctum 专属类型。
 * 对外按格式分包导出；各格式的解析实现位于对应包内。</p>
 */
module com.flora.sanctum.vault.formats {
    requires java.xml;
    requires com.flora.root;
    requires com.flora.sanctum.kdbx;

    exports com.flora.sanctum.vault.formats;
    exports com.flora.sanctum.vault.formats.bitwarden;
    exports com.flora.sanctum.vault.formats.kp1;
    exports com.flora.sanctum.vault.formats.opvault;
    exports com.flora.sanctum.vault.formats.onepux;
}
