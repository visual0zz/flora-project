package com.flora.shell;

import com.flora.shell.spec.ArgSpec;

import java.util.List;

/**
 * 命令接口：一个命令一个类，自描述名称、参数、帮助与执行逻辑。
 * <p>声明层（{@code name}/{@code description}/{@code args}/{@code usage}）是 help、
 * 参数解析、Agent schema 的共同来源；执行层（{@code execute}）通过 {@link Invocation}
 * 读参数、写输出，不直接触碰 {@code System.out}。</p>
 * <p>命令应无状态：领域状态由业务代码通过 {@link ScopedValue} 在调用前绑定，
 * 命令在 {@code execute} 内用 {@code ScopedValue.get(...)} 读取，不存于命令或框架内。</p>
 * <p>可选的接入方式特化接口见 {@link CliCommand}、{@link AgentToolCommand}。
 * "不实现任何特化接口"= 各接入方式通用。每个命令必须声明允许触发它的来源渠道
 * （见 {@link #allowedSourcePattern()}）。</p>
 */
public interface Command {

    /**
     * @return 命令名（子命令用 '.' 分隔，如 {@code buffer.write}）
     */
    String name();

    /**
     * @return 一句话说明
     */
    String description();

    /**
     * @return 参数声明，默认空
     */
    default List<ArgSpec> args() {
        return List.of();
    }

    /**
     * @return 手写一行用法覆盖自动生成的用法；{@code null} 表示自动生成
     */
    default String usage() {
        return null;
    }

    /**
     * 同名冲突裁决优先级：内置指令为负，用户命令可覆写内置指令。
     *
     * @return 优先级，默认 0
     */
    default int priority() {
        return 0;
    }

    /**
     * 执行一次调用。
     *
     * @param ctx 调用上下文
     * @return 执行结果
     * @throws Exception 命令内部错误
     */
    CommandResult execute(Invocation ctx) throws Exception;

    /**
     * 声明允许触发本命令的来源渠道匹配模式。
     * <p>返回一个正则表达式，对 {@link ChannelId#id()} 做全匹配：来源 id 匹配该正则
     * 时允许执行，否则在分派前拒绝。例如 {@code "argv"} 仅允许命令行、{@code "agent"}
     * 仅允许 Agent、{@code ".*"} 表示不限制。返回 {@code null} 视同不限制。</p>
     *
     * @return 允许的来源 id 正则；{@code null} 表示不限制
     */
    String allowedSourcePattern();
}
