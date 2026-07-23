package com.flora.log;


/**
 * 日志记录器接口，定义了日志输出的基本方法。
 * <p>
 * 支持 TRACE、DEBUG、INFO、WARN、ERROR 五个级别，
 * 每个级别提供判断是否启用和记录消息的重载方法。
 */
public interface Logger {

    /**
     * 获取日志记录器名称。
     *
     * @return 记录器名称
     */
    String getName();

    
    /**
     * 判断 TRACE 级别是否启用。
     *
     * @return 如果 TRACE 级别已启用，返回 true
     */
    boolean isTraceEnabled();

    /**
     * 记录 TRACE 级别的日志消息。
     *
     * @param msg 日志消息
     */
    void trace(String msg);

    /**
     * 记录 TRACE 级别的格式化日志消息。
     *
     * @param format 消息格式（含 {} 占位符）
     * @param args   占位符参数
     */
    void trace(String format, Object... args);

    
    /**
     * 判断 DEBUG 级别是否启用。
     *
     * @return 如果 DEBUG 级别已启用，返回 true
     */
    boolean isDebugEnabled();

    /**
     * 记录 DEBUG 级别的日志消息。
     *
     * @param msg 日志消息
     */
    void debug(String msg);

    /**
     * 记录 DEBUG 级别的格式化日志消息。
     *
     * @param format 消息格式（含 {} 占位符）
     * @param args   占位符参数
     */
    void debug(String format, Object... args);

    
    /**
     * 判断 INFO 级别是否启用。
     *
     * @return 如果 INFO 级别已启用，返回 true
     */
    boolean isInfoEnabled();

    /**
     * 记录 INFO 级别的日志消息。
     *
     * @param msg 日志消息
     */
    void info(String msg);

    /**
     * 记录 INFO 级别的格式化日志消息。
     *
     * @param format 消息格式（含 {} 占位符）
     * @param args   占位符参数
     */
    void info(String format, Object... args);

    
    /**
     * 判断 WARN 级别是否启用。
     *
     * @return 如果 WARN 级别已启用，返回 true
     */
    boolean isWarnEnabled();

    /**
     * 记录 WARN 级别的日志消息。
     *
     * @param msg 日志消息
     */
    void warn(String msg);

    /**
     * 记录 WARN 级别的格式化日志消息。
     *
     * @param format 消息格式（含 {} 占位符）
     * @param args   占位符参数
     */
    void warn(String format, Object... args);

    
    /**
     * 判断 ERROR 级别是否启用。
     *
     * @return 如果 ERROR 级别已启用，返回 true
     */
    boolean isErrorEnabled();

    /**
     * 记录 ERROR 级别的日志消息。
     *
     * @param msg 日志消息
     */
    void error(String msg);

    /**
     * 记录 ERROR 级别的格式化日志消息。
     *
     * @param format 消息格式（含 {} 占位符）
     * @param args   占位符参数
     */
    void error(String format, Object... args);
}
