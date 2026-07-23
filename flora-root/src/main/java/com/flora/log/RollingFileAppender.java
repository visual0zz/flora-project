package com.flora.log;

import java.io.IOException;
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

    /**
     * 滚动策略枚举。
     */
    public enum Policy {
        TIME_BASED,
        SIZE_BASED
    }

    private String name;
    private Level threshold = Level.TRACE;
    private Layout layout = new Layout("%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger - %msg%n");

    
    private Path basePath;
    
    private Path currentPath;
    
    private Policy policy = Policy.SIZE_BASED;
    
    private String datePattern = "yyyy-MM-dd";
    
    private String lastDate;
    
    private long maxSize = 10 * 1024 * 1024;
    
    private int maxHistory = 7;

    private FileAppender delegate;

    public RollingFileAppender() {
    }

    public RollingFileAppender(String file) {
        this.basePath = Paths.get(file);
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
    public RollingFileAppender policy(Policy policy) {
        this.policy = policy;
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

    public Policy getPolicy() {
        return policy;
    }

    public long getMaxSize() {
        return maxSize;
    }

    public int getMaxHistory() {
        return maxHistory;
    }

    /**
     * 追加日志事件，在写入前检查是否需要进行文件滚动。
     *
     * @param event 日志事件
     */
    @Override
    public synchronized void append(LogEvent event) {
        if (!event.getLevel().isEnabled(threshold)) {
            return;
        }

        if (checkRoll()) {
            roll();
        }

        if (delegate == null) {
            delegate = createDelegate();
        }

        delegate.append(event);
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
            case TIME_BASED -> {
                String today = new SimpleDateFormat(datePattern).format(new Date());
                if (lastDate == null) {
                    lastDate = today;
                    return false;
                }
                return !today.equals(lastDate);
            }
            case SIZE_BASED -> {
                try {
                    return delegate != null && Files.exists(currentPath)
                            && Files.size(currentPath) >= maxSize;
                } catch (IOException e) {
                    return false;
                }
            }
            default -> {
                return false;
            }
        }
    }

    
    /**
     * 执行文件滚动操作。
     * <p>
     * 基于时间策略：将当前文件重命名为带日期后缀的归档文件。
     * 基于大小策略：将历史文件依次重命名（.1 → .2, .2 → .3 ...），然后将当前文件重命名为 .1。
     */
    private void roll() {
        if (delegate != null) {
            delegate.close();
            delegate = null;
        }

        switch (policy) {
            case TIME_BASED -> {
                String today = new SimpleDateFormat(datePattern).format(new Date());
                
                Path archived = Paths.get(basePath + "." + lastDate);
                try {
                    if (Files.exists(currentPath)) {
                        Files.move(currentPath, archived);
                    }
                } catch (IOException e) {
                    System.err.println("Log roll error: " + e.getMessage());
                }
                lastDate = today;
            }
            case SIZE_BASED -> {
                
                for (int i = maxHistory - 1; i >= 1; i--) {
                    Path old = Paths.get(basePath + "." + i);
                    Path newer = Paths.get(basePath + "." + (i + 1));
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
                        Files.move(currentPath, Paths.get(basePath + ".1"));
                    }
                } catch (IOException e) {
                    System.err.println("Log roll error: " + e.getMessage());
                }
            }
        }

        currentPath = resolveCurrentPath();
        delegate = createDelegate();
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
        if (policy == Policy.TIME_BASED) {
            String today = new SimpleDateFormat(datePattern).format(new Date());
            return Paths.get(basePath + "." + today);
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
        fa.file(currentPath.toString());
        fa.setLayout(layout);
        fa.setThreshold(Level.TRACE); 
        return fa;
    }
}
