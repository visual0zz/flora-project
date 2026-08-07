/**
 * tangle Java 字节码混淆器模块。
 * <p>
 * 依赖 flora-root 提供基础能力；仅在测试编译时依赖 {@code java.compiler}（javax.tools）。
 */
module com.flora.tangle {
    requires com.flora.root;
    requires static java.compiler; // 仅在测试编译时使用 javax.tools

    // 字节码（class 文件）解析与混淆公开 API
    exports com.flora.classfile;
}
