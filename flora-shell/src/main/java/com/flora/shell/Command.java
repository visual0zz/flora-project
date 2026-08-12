package com.flora.shell;

import com.flora.shell.spec.ArgSpec;

import java.util.List;
import java.util.Set;

/**
 * 命令接口：一个命令一个类，自描述名称、参数、帮助与执行逻辑。
 * <p>声明层（{@code name}/{@code description}/{@code args}/{@code usage}）是 help、
 * 参数解析、Agent schema 的共同来源；执行层（{@code execute}）通过 {@link Invocation}
 * 读参数、写输出，不直接触碰 {@code System.out}。</p>
 * <p>命令应无状态：状态一律放调用方传入的 {@code Invocation.state()} 或组件外的领域对象。</p>
 * <p>可选的接入方式特化接口见 {@link CliView}、{@link AgentView}、{@link SourceRestricted}。
 * "不实现任何特化接口"= 各接入方式通用。</p>
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
     * 批量入口专属特化：argv 级前置校验 / 定制错误输出。
     * <p>实现此接口只对批量入口生效；{@code beforeExecute} 返回 {@code null} 表示通过，
     * 返回非 null 字符串表示错误消息（框架据此报错并置非零退出码）。</p>
     */
    interface CliView {
        /**
         * @param rawArgs 原始 argv（不含命令名）
         * @return {@code null} 表示校验通过；否则返回错误消息
         */
        default String beforeExecute(List<String> rawArgs) {
            return null;
        }
    }

    /**
     * Agent 专属特化：定制工具描述 / 返回值 schema。
     * <p>实现此接口只对 Agent 接入生效；未实现时框架由声明自动生成工具描述。</p>
     */
    interface AgentView {
        /**
         * @return 该命令的 Agent 工具 schema 描述文本
         */
        default String toolSchema() {
            return "";
        }
    }

    /**
     * 来源限制：声明允许触发本命令的渠道白名单。
     * <p>空集合表示不限制。例如 {@code gui}/{@code exit} 不允许微信触发时在此声明白名单。
     * 实现此接口后，组件在分派前检查来源，来源不在白名单时拒绝执行。</p>
     */
    interface SourceRestricted {
        /**
         * @return 允许触发本命令的渠道集合；空表示不限制
         */
        Set<ChannelId> allowedSources();
    }
}
