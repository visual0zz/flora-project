/**
 * flora-sanctum-cli 模块定义文件。
 * <p>
 * 命令行入口，依赖 core，作为 core 冒烟测试与脚本化入口。
 * 本模块使用 AGPL-3.0 许可证（见 flora-sanctum/LICENSE）。
 */
module com.flora.sanctum.cli {
    exports com.flora.sanctum.cli;

    requires com.flora.sanctum.core;
}
