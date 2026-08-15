package com.flora.shell;

import com.flora.shell.spec.ArgSpec;

import java.util.List;

/**
 * 命令接口：一个命令一个类，自描述名称、参数、帮助与执行逻辑。
 * <p>声明层（{@code name}/{@code description}/{@code args}/{@code usage}）是 help、
 * 参数解析的共同来源；执行层（{@code execute}）通过 {@link Invocation}
 * 读参数、返回结果，不直接触碰 {@code System.out}。</p>
 * <p>命令应无状态：领域状态由业务代码通过 {@link ScopedValue} 在调用前绑定，
 * 命令在 {@code execute} 内用 {@code ScopedValue.get(...)} 读取，不存于命令或框架内。</p>
 * <p>每个命令自报可用使用场景（见 {@link #usageScenarios()}），且只能注册进绑定对应场景的
 * {@link CommandService}。</p>
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
        StringBuilder sb = new StringBuilder(name());
        for (ArgSpec a : args()) {
            if (a.kind() == ArgSpec.Kind.POSITIONAL) {
                if (!a.required()) {
                    sb.append(" [<").append(a.name()).append('>');
                } else {
                    sb.append(" <").append(a.name()).append('>');
                }
                if (a.variadic()) {
                    sb.append("...");
                }
                if (!a.required()) {
                    sb.append(']');
                }
            } else if (a.required()) {
                sb.append(" --").append(a.name());
            }
        }
        return sb.toString();
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
     * 声明本命令可用的使用场景列表。
     * <p>命令只能注册进绑定这些场景之一的 {@link CommandService}。默认返回全部场景，
     * 表示各接入方式通用；仅限某场景的命令覆写此方法缩小范围。</p>
     *
     * @return 可用使用场景，非空
     */
    default List<UsageScenario> usageScenarios() {
        return List.of(UsageScenario.values());
    }
}
