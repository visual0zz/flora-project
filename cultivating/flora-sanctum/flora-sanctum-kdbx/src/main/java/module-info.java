/**
 * flora-sanctum-kdbx 模块：KeePass / KeePassXC (KDBX) 解密与读取。
 * <p>仅依赖 JDK 与 {@code flora-root}（密码学原语），不依赖 core 或任何 Sanctum 专属类型，
 * 可独立复用于任意需要读取 KDBX 仓库的场景。对外仅导出通用 API 包（{@code com.flora.sanctum.kdbx}），
 * 解析实现位于未导出的 {@code internal} 包。</p>
 */
module com.flora.sanctum.kdbx {
    requires java.xml;
    requires com.flora.root;

    exports com.flora.sanctum.kdbx;
}
