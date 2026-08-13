/**
 * flora-shell 命令框架模块。
 * <p>
 * 零依赖的 CLI 命令框架：一个命令 = 一个 {@code Command} 类，自描述名称、参数与帮助；
 * {@code CommandService} 负责注册、串行分派与输出扇出，无状态、无 UI。
 * 同时服务于命令行批量调用（经 {@code InputEvent.ofCliArgs}）与 AI Agent 结构化调用。
 */
module com.flora.shell {
    requires com.flora.root;

    // 命令框架核心 API
    exports com.flora.shell;
    // 参数声明与解析
    exports com.flora.shell.spec;
    // 内置指令
    exports com.flora.shell.builtin;
    // 帮助聚合与渲染
    exports com.flora.shell.help;
    // 输出接口与扇出
    exports com.flora.shell.output;
}
