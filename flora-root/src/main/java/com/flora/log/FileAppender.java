package com.flora.log;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;


/**
 * 文件日志附加器，将日志输出到指定文件。
 * <p>
 * 支持追加模式和自定义布局格式。线程安全（同步写入）。
 */
public class FileAppender implements Appender {

    private String name;
    private Level threshold = Level.TRACE;
    private Layout layout = new Layout("%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger - %msg%n");
    private Path filePath;
    private Writer writer;
    private boolean append = true;

    public FileAppender() {
    }

    public FileAppender(String file) {
        this.filePath = Paths.get(file);
    }

    

    /**
     * 设置输出文件路径（流式 API）。
     *
     * @param file 文件路径
     * @return 当前 FileAppender 实例
     */
    public FileAppender file(String file) {
        this.filePath = Paths.get(file);
        return this;
    }

    /**
     * 设置是否追加模式（流式 API）。
     *
     * @param append 如果为 true，日志追加到文件末尾
     * @return 当前 FileAppender 实例
     */
    public FileAppender append(boolean append) {
        this.append = append;
        return this;
    }

    /**
     * 设置日志输出格式（流式 API）。
     *
     * @param pattern 布局模式字符串
     * @return 当前 FileAppender 实例
     */
    public FileAppender pattern(String pattern) {
        this.layout = new Layout(pattern);
        return this;
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
     * 获取当前输出文件的路径。
     *
     * @return 文件路径
     */
    public Path getFilePath() {
        return filePath;
    }

    /**
     * 追加日志事件到文件。
     * <p>
     * 检查阈值级别后，确保文件已打开并写入格式化后的日志内容。
     *
     * @param event 日志事件
     */
    @Override
    public synchronized void append(LogEvent event) {
        if (!threshold.isEnabled(event.getLevel())) {
            return;
        }
        try {
            ensureOpen();
            writer.write(layout.format(event));
            writer.flush();
        } catch (IOException e) {
            System.err.println("Log error: " + e.getMessage());
        }
    }

    /**
     * 关闭文件写入器，释放资源。
     */
    @Override
    public synchronized void close() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException ignored) {
            }
            writer = null;
        }
    }

    /**
     * 确保文件写入器已打开，如果尚未打开则创建。
     * <p>
     * 自动创建父目录，根据 append 模式选择追加或覆盖方式。
     *
     * @throws IOException 如果文件创建失败
     * @throws IllegalStateException 如果未设置文件路径
     */
    private void ensureOpen() throws IOException {
        if (writer == null) {
            if (filePath == null) {
                throw new IllegalStateException("File path not set");
            }
            Files.createDirectories(filePath.getParent());
            writer = new BufferedWriter(new OutputStreamWriter(
                    Files.newOutputStream(filePath, append ? StandardOpenOption.APPEND : StandardOpenOption.CREATE)));
        }
    }
}
