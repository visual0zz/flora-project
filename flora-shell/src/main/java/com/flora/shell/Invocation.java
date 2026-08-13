package com.flora.shell;

import com.flora.root.java.CheckUtil;
import com.flora.shell.output.Output;
import com.flora.shell.spec.ParsedArgs;

import java.util.List;
import java.util.Map;

/**
 * 调用上下文：一次具体命令执行的环境。
 * <p>聚合命令、解析后的参数、输出门面（{@link Output}）与来源渠道。
 * 命令在 {@code execute} 中通过本对象读参数、写输出、发起转发。</p>
 * <p>领域状态不由本对象承载：业务代码通过 {@link ScopedValue} 自行绑定，命令在
 * {@code execute} 内用 {@code ScopedValue.get(...)} 读取。框架不持有、不透传状态。</p>
 * <p>命令需要把请求转给另一个命令时，用 {@link #forward}（内部委托 {@link #dispatcher}，
 * 重入完整分派管线），如用于构建 alias、--help、子命令分发等能力。</p>
 */
public final class Invocation {

    private final Command command;
    private final ParsedArgs args;
    private final Output out;
    private final ChannelId source;
    private final Dispatcher dispatcher;

    /**
     * @param command    被执行的命令
     * @param args       解析后的参数
     * @param out        输出门面
     * @param source     触发本次调用的来源渠道
     * @param dispatcher 分派门面（用于命令发起转发）
     */
    public Invocation(Command command, ParsedArgs args, Output out, ChannelId source, Dispatcher dispatcher) {
        this.command = CheckUtil.notNull(command, "命令不能为空");
        this.args = CheckUtil.notNull(args, "参数不能为空");
        this.out = CheckUtil.notNull(out, "输出门面不能为空");
        this.source = CheckUtil.notNull(source, "来源渠道不能为空");
        this.dispatcher = CheckUtil.notNull(dispatcher, "分派门面不能为空");
    }

    /**
     * @return 被执行的命令
     */
    public Command command() {
        return command;
    }

    /**
     * @return 解析后的参数
     */
    public ParsedArgs args() {
        return args;
    }

    /**
     * @return 输出门面
     */
    public Output out() {
        return out;
    }

    /**
     * @return 触发本次调用的来源渠道
     */
    public ChannelId source() {
        return source;
    }

    /**
     * @return 分派门面（命令发起转发的入口）
     */
    public Dispatcher dispatcher() {
        return dispatcher;
    }

    /**
     * 把本次请求转给另一个命令（argv 形态），沿用当前来源渠道。
     *
     * @param targetCommand 目标命令名
     * @param argv          目标命令的参数（不含命令名）
     * @return 目标命令的执行结果
     */
    public CommandResult forward(String targetCommand, List<String> argv) {
        return dispatcher.submit(InputEvent.ofArgv(source, targetCommand, argv));
    }

    /**
     * 把本次请求转给另一个命令（结构化形态），沿用当前来源渠道。
     *
     * @param targetCommand 目标命令名
     * @param params        参数名 → 值
     * @return 目标命令的执行结果
     */
    public CommandResult forward(String targetCommand, Map<String, Object> params) {
        return dispatcher.submit(InputEvent.ofStructured(source, targetCommand, params));
    }
}
