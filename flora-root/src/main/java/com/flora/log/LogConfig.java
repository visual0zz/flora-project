package com.flora.log;

import java.util.function.Consumer;


/**
 * 日志配置入口类，提供流式 API 配置日志系统。
 * <p>
 * 使用示例：
 * <pre>
 * LogConfig.configure(c -&gt; c
 *     .rootLevel(Level.INFO)
 *     .console(cc -&gt; cc.pattern("%d{HH:mm:ss} %msg%n"))
 *     .fileAppender(fc -&gt; fc.file("/var/log/app.log")));
 * </pre>
 */
public final class LogConfig {

    

    private LogConfig() {
    }

    
    /**
     * 使用配置函数快速配置日志系统。
     *
     * @param consumer 配置函数，接收一个 LogConfig 实例
     */
    public static void configure(Consumer<LogConfig> consumer) {
        LogConfig config = new LogConfig();
        consumer.accept(config);
    }

    

    
    /**
     * 设置根日志器的日志级别。
     *
     * @param level 要设置的级别
     * @return 当前 LogConfig 实例（流式 API）
     */
    public LogConfig rootLevel(Level level) {
        LoggerImpl root = LoggerFactory.getRootLogger();
        root.setLevel(level);
        return this;
    }

    
    /**
     * 添加控制台输出附加器。
     *
     * @param consumer 控制台配置函数
     * @return 当前 LogConfig 实例（流式 API）
     */
    public LogConfig console(Consumer<ConsoleConfig> consumer) {
        ConsoleConfig c = new ConsoleConfig();
        consumer.accept(c);
        ConsoleAppender appender = new ConsoleAppender();
        appender.setName(c.name != null ? c.name : "console");
        if (c.pattern != null) appender.setLayout(new Layout(c.pattern));
        if (c.threshold != null) appender.setThreshold(c.threshold);
        LoggerFactory.getRootLogger().addAppender(appender);
        return this;
    }

    
    /**
     * 添加文件输出附加器。
     *
     * @param consumer 文件配置函数
     * @return 当前 LogConfig 实例（流式 API）
     */
    public LogConfig fileAppender(Consumer<FileConfig> consumer) {
        FileConfig c = new FileConfig();
        consumer.accept(c);
        FileAppender appender = new FileAppender();
        if (c.file != null) appender.file(c.file);
        appender.setName(c.name != null ? c.name : "file");
        if (c.pattern != null) appender.setLayout(new Layout(c.pattern));
        if (c.threshold != null) appender.setThreshold(c.threshold);
        LoggerFactory.getRootLogger().addAppender(appender);
        return this;
    }

    
    /**
     * 添加滚动文件输出附加器。
     *
     * @param consumer 滚动文件配置函数
     * @return 当前 LogConfig 实例（流式 API）
     */
    public LogConfig rollingFile(Consumer<RollingConfig> consumer) {
        RollingConfig c = new RollingConfig();
        consumer.accept(c);
        RollingFileAppender appender = new RollingFileAppender();
        if (c.file != null) appender.file(c.file);
        appender.setName(c.name != null ? c.name : "rollingFile");
        if (c.pattern != null) appender.setLayout(new Layout(c.pattern));
        if (c.threshold != null) appender.setThreshold(c.threshold);
        if (c.policy != null) appender.policy(c.policy);
        if (c.datePattern != null) appender.datePattern(c.datePattern);
        if (c.maxSize > 0) appender.maxSize(c.maxSize);
        if (c.maxHistory > 0) appender.maxHistory(c.maxHistory);
        LoggerFactory.getRootLogger().addAppender(appender);
        return this;
    }

    
    /**
     * 添加按级别路由的文件附加器，只有指定级别及以上的日志会输出到该文件。
     *
     * @param level    路由级别
     * @param consumer 文件配置函数
     * @return 当前 LogConfig 实例（流式 API）
     */
    public LogConfig levelRouting(Level level, Consumer<FileConfig> consumer) {
        FileConfig c = new FileConfig();
        consumer.accept(c);
        FileAppender appender = new FileAppender();
        if (c.file != null) appender.file(c.file);
        appender.setName(c.name != null ? c.name : "level-" + level.name().toLowerCase());
        if (c.pattern != null) appender.setLayout(new Layout(c.pattern));
        appender.setThreshold(level); 
        LoggerFactory.getRootLogger().addAppender(appender);
        return this;
    }

    
    /**
     * 配置特定名称的日志器。
     *
     * @param name     日志器名称
     * @param consumer 日志器配置函数
     * @return 当前 LogConfig 实例（流式 API）
     */
    public LogConfig logger(String name, Consumer<LoggerConfig> consumer) {
        LoggerConfig lc = new LoggerConfig();
        consumer.accept(lc);
        LoggerImpl logger = (LoggerImpl) LoggerFactory.getLogger(name);
        if (lc.level != null) logger.setLevel(lc.level);
        logger.setAdditivity(lc.additivity);
        if (lc.appenders != null) {
            for (Appender a : lc.appenders) {
                logger.addAppender(a);
            }
        }
        return this;
    }

    

    
    /**
     * 控制台输出配置。
     */
    public static class ConsoleConfig {
        String name;
        String pattern;
        Level threshold;

        public ConsoleConfig name(String name) { this.name = name; return this; }
        public ConsoleConfig pattern(String pattern) { this.pattern = pattern; return this; }
        public ConsoleConfig threshold(Level threshold) { this.threshold = threshold; return this; }
    }

    
    public static class FileConfig {
        String name;
        String file;
        String pattern;
        Level threshold;

        public FileConfig name(String name) { this.name = name; return this; }
        public FileConfig file(String file) { this.file = file; return this; }
        public FileConfig pattern(String pattern) { this.pattern = pattern; return this; }
        public FileConfig threshold(Level threshold) { this.threshold = threshold; return this; }
    }

    
    public static class RollingConfig {
        String name;
        String file;
        String pattern;
        Level threshold;
        RollingFileAppender.Policy policy;
        String datePattern;
        long maxSize;
        int maxHistory;

        public RollingConfig name(String name) { this.name = name; return this; }
        public RollingConfig file(String file) { this.file = file; return this; }
        public RollingConfig pattern(String pattern) { this.pattern = pattern; return this; }
        public RollingConfig threshold(Level threshold) { this.threshold = threshold; return this; }
        public RollingConfig rolling(RollingFileAppender.Policy policy, String datePattern) {
            this.policy = policy; this.datePattern = datePattern; return this;
        }
        public RollingConfig maxSize(long maxSize) { this.maxSize = maxSize; return this; }
        public RollingConfig maxHistory(int maxHistory) { this.maxHistory = maxHistory; return this; }
    }

    
    public static class LoggerConfig {
        Level level;
        boolean additivity = true;
        java.util.List<Appender> appenders;

        public LoggerConfig level(Level level) { this.level = level; return this; }
        public LoggerConfig additivity(boolean additivity) { this.additivity = additivity; return this; }
        public LoggerConfig append(Appender appender) {
            if (appenders == null) appenders = new java.util.ArrayList<>();
            appenders.add(appender);
            return this;
        }
    }
}
