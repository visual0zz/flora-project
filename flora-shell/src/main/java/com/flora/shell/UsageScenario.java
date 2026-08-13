package com.flora.shell;

/**
 * 使用场景：一次命令调用所处的接入环境。
 * <p>每个 {@link Command} 自报其可用的使用场景列表（{@link Command#usageScenarios()}），
 * 每个 {@link CommandService} 实例在构造时绑定一个场景，仅接受声明支持该场景的命令注册。
 * {@link InputEvent} 的来源（{@link InputEvent#source()}）即一次调用的使用场景。</p>
 */
public enum UsageScenario {
    /** 命令行批量调用（argv）。 */
    CLI,
    /** AI Agent 结构化调用。 */
    AGENT
    // 未来增量：TUI、GUI、WECHAT 等
}
