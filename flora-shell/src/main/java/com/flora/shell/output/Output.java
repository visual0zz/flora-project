package com.flora.shell.output;

/**
 * 输出门面。
 * <p>命令不再直接写输出，而是把文本放进 {@link com.flora.shell.CommandResult}；
 * {@link com.flora.shell.CommandService} 在 {@code execute} 返回后通过本接口把
 * 结果的 {@code output()}/{@code error()} 扇出。这样同一份命令实现可同时跑在
 * 批量打印、未来 TUI 面板与微信回写上。</p>
 * <p>实现方（通常为 {@link com.flora.shell.output.OutputMultiplexer}）负责把每次调用
 * 扇出到所有已挂载的 {@link com.flora.shell.output.OutputSink}；无挂载 sink 时退化直达
 * stdout/stderr。</p>
 */
public interface Output {

    /**
     * 输出文本，不追加换行。
     *
     * @param s 文本
     */
    void print(String s);

    /**
     * 输出文本并追加换行。
     *
     * @param s 文本
     */
    void println(String s);

    /**
     * 输出一行错误文本。
     *
     * @param s 错误文本
     */
    void error(String s);
}
