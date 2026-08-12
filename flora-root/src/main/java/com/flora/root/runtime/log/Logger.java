package com.flora.root.runtime.log;

import java.util.function.Supplier;


/**
 * 日志记录器接口，定义了日志输出的基本方法。
 * <p>
 * 支持 TRACE、DEBUG、INFO、WARN、ERROR、FATAL 六个级别，
 * 每个级别提供判断是否启用和记录消息的重载方法。
 * <p>
 * 消息来源有三种形态：
 * <ul>
 *   <li>普通字符串 {@code void info(String)}；</li>
 *   <li>含 {@code {}} 占位符的格式化模板 {@code void info(String, Object...)}；</li>
 *   <li>惰性求值的 {@link Supplier}{@code <String>} {@code void info(Supplier<String>)}，
 *        仅当该级别启用时才调用 {@code get()}，避免无谓的字符串拼接开销。</li>
 * </ul>
 * 每个级别另提供 {@code (String, Throwable)} 重载用于关联异常堆栈。
 * <p>
 * 含占位符的重载（{@code (String, Object...)}）遵循与 SLF4J/log4j2 一致的习惯：
 * 若参数数组的最后一个元素是 {@link Throwable}，则自动将其作为关联异常剥离，其余参数用于占位符填充，
 * 因此 {@code error("msg {}", arg, ex)} 与 {@code error("msg {}", arg, (Throwable) ex)} 等价。
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

    /**
     * 判断 FATAL 级别是否启用。
     *
     * @return 如果 FATAL 级别已启用，返回 true
     */
    boolean isFatalEnabled();

    /**
     * 记录 FATAL 级别的日志消息。
     *
     * @param msg 日志消息
     */
    void fatal(String msg);

    /**
     * 记录 FATAL 级别的格式化日志消息。
     *
     * @param format 消息格式（含 {} 占位符）
     * @param args   占位符参数
     */
    void fatal(String format, Object... args);


    // ==================== 异常关联重载 ====================

    /**
     * 记录 TRACE 级别的日志消息，并关联异常堆栈。
     *
     * @param msg 日志消息
     * @param t   关联的异常
     */
    void trace(String msg, Throwable t);

    /**
     * 记录 DEBUG 级别的日志消息，并关联异常堆栈。
     *
     * @param msg 日志消息
     * @param t   关联的异常
     */
    void debug(String msg, Throwable t);

    /**
     * 记录 INFO 级别的日志消息，并关联异常堆栈。
     *
     * @param msg 日志消息
     * @param t   关联的异常
     */
    void info(String msg, Throwable t);

    /**
     * 记录 WARN 级别的日志消息，并关联异常堆栈。
     *
     * @param msg 日志消息
     * @param t   关联的异常
     */
    void warn(String msg, Throwable t);

    /**
     * 记录 ERROR 级别的日志消息，并关联异常堆栈。
     *
     * @param msg 日志消息
     * @param t   关联的异常
     */
    void error(String msg, Throwable t);

    /**
     * 记录 FATAL 级别的日志消息，并关联异常堆栈。
     *
     * @param msg 日志消息
     * @param t   关联的异常
     */
    void fatal(String msg, Throwable t);


    // ==================== 惰性求值重载 ====================

    /**
     * 记录 TRACE 级别的日志消息，消息由 {@link Supplier} 惰性提供。
     * <p>仅当 TRACE 级别启用时才调用 {@code message.get()}。</p>
     *
     * @param message 惰性提供消息的供应商
     */
    void trace(Supplier<String> message);

    /**
     * 记录 DEBUG 级别的日志消息，消息由 {@link Supplier} 惰性提供。
     * <p>仅当 DEBUG 级别启用时才调用 {@code message.get()}。</p>
     *
     * @param message 惰性提供消息的供应商
     */
    void debug(Supplier<String> message);

    /**
     * 记录 INFO 级别的日志消息，消息由 {@link Supplier} 惰性提供。
     * <p>仅当 INFO 级别启用时才调用 {@code message.get()}。</p>
     *
     * @param message 惰性提供消息的供应商
     */
    void info(Supplier<String> message);

    /**
     * 记录 WARN 级别的日志消息，消息由 {@link Supplier} 惰性提供。
     * <p>仅当 WARN 级别启用时才调用 {@code message.get()}。</p>
     *
     * @param message 惰性提供消息的供应商
     */
    void warn(Supplier<String> message);

    /**
     * 记录 ERROR 级别的日志消息，消息由 {@link Supplier} 惰性提供。
     * <p>仅当 ERROR 级别启用时才调用 {@code message.get()}。</p>
     *
     * @param message 惰性提供消息的供应商
     */
    void error(Supplier<String> message);

    /**
     * 记录 FATAL 级别的日志消息，消息由 {@link Supplier} 惰性提供。
     * <p>仅当 FATAL 级别启用时才调用 {@code message.get()}。</p>
     *
     * @param message 惰性提供消息的供应商
     */
    void fatal(Supplier<String> message);
}
