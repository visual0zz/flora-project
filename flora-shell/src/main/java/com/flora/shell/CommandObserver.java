package com.flora.shell;

/**
 * 命令执行观察者：每次命令执行完毕后被回调，携带本次调用的输入与结果。
 * <p>通过 {@link CommandService#newSink(CommandObserver)} 注册为 sink；lambda 形如
 * {@code (event, result) -> ...}，可消费结构化的 {@link InputEvent} 与 {@link CommandResult}，
 * 用于收集、转发、渲染（如 TUI、微信、日志）等。</p>
 */
@FunctionalInterface
public interface CommandObserver {

    /**
     * 命令执行完毕后回调。
     *
     * @param event  触发本次调用的输入
     * @param result 本次调用的执行结果
     */
    void onExecuted(InputEvent event, CommandResult result);
}
