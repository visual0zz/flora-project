package com.flora.shell;

/**
 * 命令分派门面：命令在执行期间用它发起新的调用（转发）。
 * <p>{@link Invocation} 携带本接口（由 {@link CommandService} 实现），命令在
 * {@code execute} 内通过它把请求转给另一个命令，而无需依赖具体 {@link CommandService}
 * 实现，避免循环依赖。转发会重入完整分派管线（参数解析、来源限制、输出扇出）。</p>
 */
public interface Dispatcher {

    /**
     * 提交一次调用并执行（走完整分派管线）。
     *
     * @param event 归一化输入
     * @return 执行结果
     */
    CommandResult submit(InputEvent event);
}
