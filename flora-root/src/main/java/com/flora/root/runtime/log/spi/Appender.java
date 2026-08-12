package com.flora.root.runtime.log.spi;

import com.flora.root.runtime.log.Level;

import java.nio.file.Path;


/**
 * 日志附加器接口，定义了日志输出的目的地和行为。
 * <p>
 * 实现类可以将日志输出到控制台、文件或其他目标。
 * 每个附加器可设置阈值级别和布局格式。
 */
public interface Appender {

    /**
     * 获取阈值级别，低于此级别的日志将被忽略。
     *
     * @return 阈值级别
     */
    Level getThreshold();

    /**
     * 设置阈值级别。
     *
     * @param threshold 阈值级别
     */
    void setThreshold(Level threshold);

    /**
     * 获取布局格式器。
     *
     * @return 布局格式器
     */
    Layout getLayout();

    /**
     * 设置布局格式器。
     *
     * @param layout 布局格式器
     */
    void setLayout(Layout layout);

    /**
     * 获取附加器名称。
     *
     * @return 附加器名称
     */
    String getName();

    /**
     * 设置附加器名称。
     *
     * @param name 附加器名称
     */
    void setName(String name);

    /**
     * 追加日志事件。
     *
     * @param event 日志事件
     */
    void append(LogEvent event);

    /**
     * 关闭附加器，释放资源。
     */
    default void close() {
    }

    /**
     * 返回该附加器输出的目标文件路径；非文件类附加器（如控制台）返回 {@code null}。
     * <p>供配置期检测同一日志器上多个附加器指向相同文件时使用；默认实现返回 {@code null}，
     * 文件类附加器应覆写以返回其目标路径。</p>
     *
     * @return 目标文件路径，或 {@code null}
     */
    default Path getTargetPath() {
        return null;
    }
}
