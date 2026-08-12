package com.flora.root.runtime.log.impl;

import com.flora.root.runtime.log.Level;
import com.flora.root.runtime.log.spi.Appender;
import com.flora.root.runtime.log.spi.Layout;
import com.flora.root.runtime.log.spi.LogEvent;
import com.flora.root.runtime.log.spi.RollingPolicy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;


/**
 * 滚动文件日志附加器，支持基于时间和基于大小的日志滚动策略。
 * <p>
 * 当满足滚动条件时，自动归档当前日志文件并创建新文件。
 */
public class RollingFileAppender implements Appender {

    private String name;
    private Level threshold = Level.TRACE;
    private Layout layout = new Layout("%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger - %msg%n");


    private Path basePath;

    private Path currentPath;

    private String filePattern;

    private RollingPolicy policy = RollingPolicy.SIZE_BASED;

    private String datePattern = "yyyy-MM-dd";

    private String lastDate;

    private long maxSize = 10 * 1024 * 1024;

    private int maxHistory = 7;

    private long currentSize;

    private FileAppender delegate;

    public RollingFileAppender() {
    }

    public RollingFileAppender(String file) {
        this.basePath = Paths.get(file);
    }

    /**
     * 设置基础文件路径（文件系统无关）。
     *
     * @param file 基础文件路径
     */
    public RollingFileAppender(Path file) {
        this.basePath = file;
    }

    /**
     * 设置基础文件路径（文件系统无关，流式 API）。
     *
     * @param file 基础文件路径
     * @return 当前 RollingFileAppender 实例
     */
    public RollingFileAppender file(Path file) {
        this.basePath = file;
        return this;
    }



    /**
     * 设置基础文件路径（流式 API）。
     *
     * @param file 基础文件路径
     * @return 当前 RollingFileAppender 实例
     */
    public RollingFileAppender file(String file) {
        this.basePath = Paths.get(file);
        return this;
    }

    /**
     * 设置日志输出格式（流式 API）。
     *
     * @param pattern 布局模式字符串
     * @return 当前 RollingFileAppender 实例
     */
    public RollingFileAppender pattern(String pattern) {
        this.layout = new Layout(pattern);
        return this;
    }

    /**
     * 设置滚动策略（流式 API）。
     *
     * @param policy 滚动策略
     * @return 当前 RollingFileAppender 实例
     */
    public RollingFileAppender policy(RollingPolicy policy) {
        this.policy = policy;
        return this;
    }

    /**
     * 设置归档文件命名模式（log4j 风格，流式 API）。
     * <p>
     * 模式中的 {@code %d{日期格式}}（或 {@code %d}）替换为滚动日期，
     * {@code %i} 替换为序号（大小滚动时 1 为最新归档）。
     * 例如 {@code app-%d{yyyy-MM-dd}.log} 或 {@code app-%i.log}。
     * 未设置时回退到 {@code base.log.日期} / {@code base.log.N} 的默认命名。
     * 注意：本实现仅使用模式决定文件名，不执行 {@code .gz} 等压缩。
     *
     * @param filePattern 归档文件命名模式
     * @return 当前 RollingFileAppender 实例
     */
    public RollingFileAppender filePattern(String filePattern) {
        this.filePattern = filePattern;
        return this;
    }

    /**
     * 设置基于时间滚动时的日期格式（流式 API）。
     *
     * @param datePattern 日期格式，如 "yyyy-MM-dd"
     * @return 当前 RollingFileAppender 实例
     */
    public RollingFileAppender datePattern(String datePattern) {
        this.datePattern = datePattern;
        return this;
    }

    /**
     * 设置基于大小滚动时的最大文件字节数（流式 API）。
     *
     * @param maxSizeBytes 最大字节数
     * @return 当前 RollingFileAppender 实例
     */
    public RollingFileAppender maxSize(long maxSizeBytes) {
        this.maxSize = maxSizeBytes;
        return this;
    }

    /**
     * 设置基于大小滚动时的最大历史文件数（流式 API）。
     *
     * @param maxHistory 最大历史文件数
     * @return 当前 RollingFileAppender 实例
     */
    public RollingFileAppender maxHistory(int maxHistory) {
        this.maxHistory = maxHistory;
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

    public RollingPolicy getPolicy() {
        return policy;
    }

    public String getFilePattern() {
        return filePattern;
    }

    public long getMaxSize() {
        return maxSize;
    }

    public int getMaxHistory() {
        return maxHistory;
    }

    @Override
    public Path getTargetPath() {
        return basePath;
    }

    /**
     * 追加日志事件，在写入前检查是否需要进行文件滚动。
     *
     * @param event 日志事件
     */
    @Override
    public synchronized void append(LogEvent event) {
        if (!threshold.isEnabled(event.getLevel())) {
            return;
        }

        if (checkRoll()) {
            roll();
        }

        if (delegate == null) {
            delegate = createDelegate();
        }

        delegate.append(event);
        // 自行累计已写入字节数：缓冲写入（含内存文件系统）下 Files.size 不能可靠反映
        // 当前文件大小，故由附加器跟踪，而非依赖文件系统查询。
        currentSize += layout.format(event).getBytes(StandardCharsets.UTF_8).length;
    }


    /**
     * 解析与 {@code basePath} 同目录、以 {@code suffix} 为扩展名的归档文件路径，
     * 使用 basePath 所在文件系统的路径构造，避免硬编码默认文件系统。
     *
     * @param suffix 追加在基础文件名之后的后缀（含点号）
     * @return 归档文件路径
     */
    private Path siblingWithSuffix(String suffix) {
        return basePath.resolveSibling(basePath.getFileName() + suffix);
    }

    /**
     * 关闭委托的文件附加器，释放资源。
     */
    @Override
    public synchronized void close() {
        if (delegate != null) {
            delegate.close();
            delegate = null;
        }
    }




    /**
     * 检查是否需要执行文件滚动。
     * <p>
     * 基于时间策略：检查当前日期是否已变化。
     * 基于大小策略：检查当前文件大小是否达到上限。
     *
     * @return 如果需要滚动则返回 true
     */
    private boolean checkRoll() {
        if (basePath == null) {
            return false;
        }

        switch (policy) {
            case RollingPolicy.TIME_BASED -> {
                String today = new SimpleDateFormat(datePattern).format(new Date());
                if (lastDate == null) {
                    lastDate = today;
                    return false;
                }
                return !today.equals(lastDate);
            }
            case RollingPolicy.SIZE_BASED -> {
                return delegate != null && currentSize >= maxSize;
            }
            default -> {
                return false;
            }
        }
    }


    /**
     * 执行文件滚动操作。
     * <p>
     * 基于时间策略：将当前文件重命名为带日期后缀的归档文件（或按 filePattern 命名）。
     * 基于大小策略：将历史文件依次重命名（.1 → .2, .2 → .3 ...），然后将当前文件重命名为 .1；
     * 设置了 filePattern 时则按 {@code %i} 序号归档。
     */
    private void roll() {
        if (delegate != null) {
            delegate.close();
            delegate = null;
        }

        switch (policy) {
            case RollingPolicy.TIME_BASED -> {
                String today = new SimpleDateFormat(datePattern).format(new Date());

                Path archived = filePattern != null
                        ? resolveArchivePath(0)
                        : siblingWithSuffix("." + lastDate);
                try {
                    if (Files.exists(currentPath)) {
                        Files.move(currentPath, archived);
                    }
                } catch (IOException e) {
                    System.err.println("Log roll error: " + e.getMessage());
                }
                lastDate = today;
            }
            case RollingPolicy.SIZE_BASED -> {

                for (int i = maxHistory - 1; i >= 1; i--) {
                    Path old = filePattern != null
                            ? resolveArchivePath(i)
                            : siblingWithSuffix("." + i);
                    Path newer = filePattern != null
                            ? resolveArchivePath(i + 1)
                            : siblingWithSuffix("." + (i + 1));
                    try {
                        if (Files.exists(old)) {
                            if (i == maxHistory - 1) {
                                Files.deleteIfExists(newer);
                            }
                            Files.move(old, newer);
                        }
                    } catch (IOException e) {
                        System.err.println("Log roll error: " + e.getMessage());
                    }
                }

                try {
                    if (Files.exists(currentPath)) {
                        Path archived = filePattern != null
                                ? resolveArchivePath(1)
                                : siblingWithSuffix(".1");
                        Files.move(currentPath, archived);
                    }
                } catch (IOException e) {
                    System.err.println("Log roll error: " + e.getMessage());
                }
            }
        }

        currentPath = resolveCurrentPath();
        delegate = createDelegate();
        currentSize = 0;
    }

    /**
     * 根据 filePattern 解析归档文件路径，替换 {@code %d{...}}/{@code %d} 为日期、{@code %i} 为序号。
     *
     * @param index 序号（大小滚动时 1 表示最新归档）
     * @return 解析后的归档文件路径
     */
    private Path resolveArchivePath(int index) {
        String date = new SimpleDateFormat(datePattern).format(new Date());
        String name = filePattern
                .replace("%d{" + datePattern + "}", date)
                .replace("%d", date)
                .replace("%i", Integer.toString(index));
        // 使用 basePath 所在文件系统构造归档路径，避免硬编码默认文件系统，
        // 使 filePattern 在任意文件系统（含内存虚拟文件系统）下都能正确解析。
        return basePath.getFileSystem().getPath(name);
    }

    /**
     * 根据策略解析当前输出文件的路径。
     * <p>
     * 时间策略下，路径为 basePath + "." + 当前日期；
     * 大小策略下，路径为 basePath 本身。
     *
     * @return 当前文件路径
     */
    private Path resolveCurrentPath() {
        if (policy == RollingPolicy.TIME_BASED) {
            String today = new SimpleDateFormat(datePattern).format(new Date());
            // 与 basePath 同目录、同文件系统，避免硬编码默认文件系统
            return basePath.resolveSibling(basePath.getFileName() + "." + today);
        }
        return basePath;
    }

    /**
     * 创建一个委托的 FileAppender 实例，用于实际写入日志。
     *
     * @return FileAppender 实例
     */
    private FileAppender createDelegate() {
        if (currentPath == null) {
            currentPath = resolveCurrentPath();
        }
        FileAppender fa = new FileAppender();
        fa.file(currentPath);
        fa.setLayout(layout);
        fa.setThreshold(Level.TRACE);
        return fa;
    }
}
