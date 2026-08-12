package com.flora.root.runtime.log.spi;

import com.flora.root.runtime.log.Level;


/**
 * 日志事件，封装单条日志的完整信息。
 * <p>包含日志记录器名称、日志级别、原始消息、格式化后的消息、参数、时间戳、线程信息，
 * 以及可选的异常对象与调用位置（仅当布局需要调用位置转换符时才会被填充）。</p>
 */
public class LogEvent {

    private final String loggerName;
    private final Level level;
    private final String message;
    private final String formattedMessage; // 可能已被 Masker 脱敏；程序化消费敏感字段请改用 getMessage()
    private final Object[] args;
    private final long timestamp;
    private final Thread thread;
    private final Throwable throwable;
    private final StackTraceElement callerLocation;

    /**
     * 构造日志事件（无异常、无调用位置）。
     *
     * @param loggerName       日志记录器名称
     * @param level            日志级别
     * @param message          原始日志消息（含占位符）
     * @param args             消息参数
     * @param formattedMessage 格式化后的完整消息
     */
    public LogEvent(String loggerName, Level level, String message, Object[] args, String formattedMessage) {
        this(loggerName, level, message, args, formattedMessage, null, null);
    }

    /**
     * 构造日志事件。
     *
     * @param loggerName       日志记录器名称
     * @param level            日志级别
     * @param message          原始日志消息（含占位符）
     * @param args             消息参数
     * @param formattedMessage 格式化后的完整消息
     * @param throwable        关联的异常，没有则为 null
     * @param callerLocation   触发日志调用的代码位置，未捕获则为 null
     */
    public LogEvent(String loggerName, Level level, String message, Object[] args, String formattedMessage,
                    Throwable throwable, StackTraceElement callerLocation) {
        this.loggerName = loggerName;
        this.level = level;
        this.message = message;
        this.args = args;
        this.formattedMessage = formattedMessage;
        this.timestamp = System.currentTimeMillis();
        this.thread = Thread.currentThread();
        this.throwable = throwable;
        this.callerLocation = callerLocation;
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

    /**
     * @return 关联的异常对象，没有则为 null
     */
    public Throwable getThrowable() {
        return throwable;
    }

    /**
     * @return 触发日志调用的代码位置，未捕获则为 null
     */
    public StackTraceElement getCallerLocation() {
        return callerLocation;
    }
}
