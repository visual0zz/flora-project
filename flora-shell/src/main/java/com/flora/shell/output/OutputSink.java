package com.flora.shell.output;

/**
 * 业务输出汇的最小接口。
 * <p>屏幕、微信连接、stdout 等实现此接口接收扇出的文本。
 * {@code emit} 对应命令侧的 {@code print}/{@code println}（换行由扇出层补，
 * 见 {@link com.flora.shell.output.Output}），{@code emitError} 对应 {@code error}。</p>
 * <p>本期批量场景无挂载 sink 时，扇出实现退化直达 stdout/stderr，无需业务实现本接口。</p>
 */
public interface OutputSink {

    /**
     * 输出一段普通文本。
     *
     * @param text 文本内容
     */
    void emit(String text);

    /**
     * 输出一段错误文本（如写入 stderr）。
     *
     * @param text 错误文本内容
     */
    void emitError(String text);
}
