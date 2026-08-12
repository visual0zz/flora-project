package com.flora.shell;

import com.flora.root.java.CheckUtil;
import com.flora.shell.output.Output;
import com.flora.shell.spec.ParsedArgs;

/**
 * 调用上下文：一次具体命令执行的环境。
 * <p>聚合命令、解析后的参数、输出门面（{@link Output}）与调用方状态（{@link #state()}）。
 * 命令在 {@code execute} 中通过本对象读参数、写输出、访问领域状态。</p>
 * <p>状态是调用方传入的对象，组件原样透传；命令侧通过它读写领域状态。
 * 本期单次调用场景无并发；未来多渠道共享时，领域对象的线程一致性由宿主保证（见设计文档）。</p>
 */
public final class Invocation {

    private final Command command;
    private final ParsedArgs args;
    private final Output out;
    private final ChannelId source;
    private final Object state;

    /**
     * @param command 被执行的命令
     * @param args    解析后的参数
     * @param out     输出门面
     * @param source  触发本次调用的来源渠道
     * @param state   调用方领域状态（可传 {@code null}）
     */
    public Invocation(Command command, ParsedArgs args, Output out, ChannelId source, Object state) {
        this.command = CheckUtil.notNull(command, "命令不能为空");
        this.args = CheckUtil.notNull(args, "参数不能为空");
        this.out = CheckUtil.notNull(out, "输出门面不能为空");
        this.source = CheckUtil.notNull(source, "来源渠道不能为空");
        this.state = state;
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
     * @return 调用方领域状态；可能为 {@code null}
     */
    @SuppressWarnings("unchecked")
    public <T> T state() {
        return (T) state;
    }
}
