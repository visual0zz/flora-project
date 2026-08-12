package com.flora.shell.output;

import com.flora.root.java.CheckUtil;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 输出扇出实现：把每次 {@link Output} 调用广播到所有已挂载的 {@link OutputSink}。
 * <p>无任何挂载 sink 时，普通文本退化为 {@link System#out}、错误文本退化为
 * {@link System#err}，保证批量场景开箱即用；挂载 sink 后（如未来 TUI 的 ScreenSink、
 * 微信的 WeChatSink），输出同时到达所有 sink 与 stdout/stderr。</p>
 * <p>{@code println} 的换行由扇出层补上：对每个 sink 调 {@code emit(text + "\n")}；
 * {@code print} 调 {@code emit(text)}；{@code error} 调 {@code emitError(text)}。</p>
 */
public final class OutputMultiplexer implements Output {

    private final PrintStream out;
    private final PrintStream err;
    private final List<OutputSink> sinks = new ArrayList<>();

    /**
     * 用默认 stdout/stderr 创建扇出器。
     */
    public OutputMultiplexer() {
        this(System.out, System.err);
    }

    /**
     * @param out 无 sink 时普通文本退化的输出流
     * @param err 无 sink 时错误文本退化的输出流
     */
    public OutputMultiplexer(PrintStream out, PrintStream err) {
        this.out = CheckUtil.notNull(out, "标准输出流不能为空");
        this.err = CheckUtil.notNull(err, "错误输出流不能为空");
    }

    /**
     * 挂载一个输出汇；之后的所有输出同时到达该汇。
     *
     * @param sink 输出汇
     */
    public synchronized void attach(OutputSink sink) {
        sinks.add(CheckUtil.notNull(sink, "输出汇不能为空"));
    }

    /**
     * 卸载一个输出汇。
     *
     * @param sink 输出汇
     */
    public synchronized void detach(OutputSink sink) {
        sinks.remove(sink);
    }

    /**
     * @return 当前已挂载的输出汇快照
     */
    public synchronized List<OutputSink> sinks() {
        return new ArrayList<>(sinks);
    }

    @Override
    public void print(String s) {
        if (sinks.isEmpty()) {
            out.print(s);
        } else {
            for (OutputSink sink : sinks) {
                sink.emit(s);
            }
        }
    }

    @Override
    public void println(String s) {
        if (sinks.isEmpty()) {
            out.println(s);
        } else {
            for (OutputSink sink : sinks) {
                sink.emit(s + "\n");
            }
        }
    }

    @Override
    public void error(String s) {
        if (sinks.isEmpty()) {
            err.println(s);
        } else {
            for (OutputSink sink : sinks) {
                sink.emitError(s);
            }
        }
    }
}
