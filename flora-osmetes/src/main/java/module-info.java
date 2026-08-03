/**
 * osmetes 综合源码检查模块。
 * <p>
 * 导出公开 API 包 {@code com.flora.osmetes}（检查项接口、问题模型与引擎门面），
 * 供 Maven 插件等外部消费者使用；内置检查项位于 {@code com.flora.osmetes.check}，
 * gitignore 忽略规则位于 {@code com.flora.osmetes.gitignore}，均不对外导出。
 * <p>
 * 通过 {@link com.flora.osmetes.FileCheck} SPI 允许第三方注册自定义检查项。
 */
module com.flora.osmetes {
    requires com.flora.root;

    exports com.flora.osmetes;
    uses com.flora.osmetes.FileCheck;
}
