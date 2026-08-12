package com.flora.shell.output;

/**
 * 命令写输出的唯一门面。
 * <p>命令在 {@code execute} 中通过 {@code Invocation.out()} 拿到本接口的实例，
 * 只调 {@code print}/{@code println}/{@code error} 写输出，不直接触碰 {@code System.out}。
 * 这样同一份实现可同时跑在批量打印、未来 TUI 面板与微信回写上。</p>
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
