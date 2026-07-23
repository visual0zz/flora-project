package com.flora.log;


/**
 * 日志事件，封装单条日志的完整信息。
 * <p>包含日志记录器名称、日志级别、原始消息、格式化后的消息、参数、时间戳和线程信息。</p>
 */
public class LogEvent {

    private final String loggerName;
    private final Level level;
    private final String message;
    private final String formattedMessage;
    private final Object[] args;
    private final long timestamp;
    private final Thread thread;

    /**
     * 构造日志事件。
     *
     * @param loggerName       日志记录器名称
     * @param level            日志级别
     * @param message          原始日志消息（含占位符）
     * @param args             消息参数
     * @param formattedMessage 格式化后的完整消息
     */
    LogEvent(String loggerName, Level level, String message, Object[] args, String formattedMessage) {
        this.loggerName = loggerName;
        this.level = level;
        this.message = message;
        this.args = args;
        this.formattedMessage = formattedMessage;
        this.timestamp = System.currentTimeMillis();
        this.thread = Thread.currentThread();
    }

    /**
     * @return 日志记录器名称
     */
    public String getLoggerName() {
        return loggerName;
    }

    /**
     * @return 日志级别
     */
    public Level getLevel() {
        return level;
    }

    /**
     * @return 原始日志消息（含占位符）
     */
    public String getMessage() {
        return message;
    }

    /**
     * @return 消息参数
     */
    public Object[] getArgs() {
        return args;
    }

    /**
     * @return 格式化后的完整消息
     */
    public String getFormattedMessage() {
        return formattedMessage;
    }

    /**
     * @return 事件创建时的时间戳（毫秒）
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * @return 创建该事件的线程
     */
    public Thread getThread() {
        return thread;
    }
}
