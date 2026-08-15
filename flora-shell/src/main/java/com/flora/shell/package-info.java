/**
 * CLI 命令框架核心 API。
 * <p>一个命令 = 一个 {@code Command} 类，自描述名称、参数、帮助与执行逻辑；
 * {@link com.flora.shell.CommandService} 负责注册、串行分派与结果回调，无状态、无 UI。
 * 一次输入经 {@link com.flora.shell.InputEvent} 归一化后提交给组件，经参数校验构造
 * {@link com.flora.shell.Invocation} 执行；每次执行完毕把 {@link InputEvent} 与
 * {@link CommandResult} 交给通过 {@link com.flora.shell.CommandService#newSink} 注册的
 * 观察者（见 {@link com.flora.shell.CommandSink}）。</p>
 */
package com.flora.shell;
