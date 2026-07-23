package com.flora.log;

import java.io.PrintStream;


/**
 * 控制台日志附加器，将日志输出到标准输出流或标准错误流。
 * <p>
 * ERROR 级别的日志输出到 System.err，其他级别输出到 System.out。
 */
public class ConsoleAppender implements Appender {

    private String name;
    private Level threshold = Level.TRACE;
    private Layout layout = new Layout("%d{HH:mm:ss.SSS} [%thread] %-5level %logger - %msg%n");

    public ConsoleAppender() {
    }

    @Override
    public Level getThreshold() {
        return threshold;
    }

    @Override
    public void setThreshold(Level threshold) {
        this.threshold = threshold;
    }

    @Override
    public Layout getLayout() {
        return layout;
    }

    @Override
    public void setLayout(Layout layout) {
        this.layout = layout;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 追加日志事件到控制台。
     * <p>
     * 检查阈值级别后，ERROR 级别输出到 System.err，其他级别输出到 System.out。
     *
     * @param event 日志事件
     */
    @Override
    public void append(LogEvent event) {
        if (!threshold.isEnabled(event.getLevel())) {
            return;
        }
        PrintStream out = event.getLevel() == Level.ERROR ? System.err : System.out;
        out.print(layout.format(event));
    }
}
