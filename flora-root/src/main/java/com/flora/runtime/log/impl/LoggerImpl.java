package com.flora.runtime.log.impl;
import com.flora.tag.ThreadFragile;

import com.flora.runtime.log.Level;
import com.flora.runtime.log.Logger;
import com.flora.runtime.log.LoggerFactory;
import com.flora.runtime.log.spi.Appender;
import com.flora.runtime.log.spi.Layout;
import com.flora.runtime.log.spi.LogEvent;
import com.flora.runtime.log.spi.Masker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;


/**
 * Logger 接口的默认实现。
 * <p>
 * 维护日志级别、附加器列表和层级追加功能（additivity）。
 * 通过 LoggerFactory 获取有效级别，并支持向父级日志器传递日志事件。
 */
@ThreadFragile
public final class LoggerImpl implements Logger {

    private final String name;
    private Level level;
    private boolean additivity = true;
    private final List<Appender> appenders = Collections.synchronizedList(new ArrayList<>());
    private Masker masker = LoggerFactory.defaultMasker();


    volatile Level effectiveLevel;

    public LoggerImpl(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }



    /**
     * 设置日志级别。
     *
     * @param level 要设置的日志级别
     */
    public void setLevel(Level level) {
        this.level = level;
        recomputeEffectiveLevel();
    }

    /**
     * 获取当前日志级别。
     *
     * @return 当前级别，可能为 null（此时使用有效级别）
     */
    public Level getLevel() {
        return level;
    }

    /**
     * 设置是否向父级日志器传递日志事件。
     *
     * @param additivity 如果为 true，日志事件将继续向上传递
     */
    public void setAdditivity(boolean additivity) {
        this.additivity = additivity;
    }

    /**
     * 检查是否向父级日志器传递日志事件。
     *
     * @return additivity 标志
     */
    public boolean isAdditivity() {
        return additivity;
    }

    /**
     * 添加一个附加器。
     *
     * @param appender 要添加的附加器
     */
    public void addAppender(Appender appender) {
        appenders.add(appender);
    }

    /**
     * 设置脱敏器，作用于本日志器输出的消息文本。
     * 传入 {@link Masker#NONE} 可关闭脱敏。
     *
     * @param masker 脱敏器，不允许为 null
     */
    public void setMasker(Masker masker) {
        this.masker = masker != null ? masker : Masker.NONE;
    }

    /**
     * 获取所有附加器的列表。
     *
     * @return 附加器列表（线程安全）
     */
    public List<Appender> getAppenders() {
        return appenders;
    }

    /**
     * 获取有效日志级别，优先使用当前设置的级别，否则从父级继承。
     *
     * @return 有效日志级别
     */
    public Level getEffectiveLevel() {
        return LoggerFactory.getEffectiveLevel(name);
    }

    /**
     * 重新计算有效级别：如果当前已设置级别则使用之，否则从 LoggerFactory 获取。
     */
    public void recomputeEffectiveLevel() {
        if (level != null) {
            effectiveLevel = level;
        } else {
            effectiveLevel = LoggerFactory.getEffectiveLevel(name);
        }
    }



    @Override
    public boolean isTraceEnabled() {
        return LoggerFactory.getEffectiveLevel(name).isEnabled(Level.TRACE);
    }

    @Override
    public boolean isDebugEnabled() {
        return LoggerFactory.getEffectiveLevel(name).isEnabled(Level.DEBUG);
    }

    @Override
    public boolean isInfoEnabled() {
        return LoggerFactory.getEffectiveLevel(name).isEnabled(Level.INFO);
    }

    @Override
    public boolean isWarnEnabled() {
        return LoggerFactory.getEffectiveLevel(name).isEnabled(Level.WARN);
    }

    @Override
    public boolean isErrorEnabled() {
        return LoggerFactory.getEffectiveLevel(name).isEnabled(Level.ERROR);
    }

    @Override
    public boolean isFatalEnabled() {
        return LoggerFactory.getEffectiveLevel(name).isEnabled(Level.FATAL);
    }


    @Override
    public void trace(String msg) {
        log(Level.TRACE, msg, null, null);
    }

    @Override
    public void trace(String format, Object... args) {
        log(Level.TRACE, format, args, null);
    }

    @Override
    public void trace(String msg, Throwable t) {
        log(Level.TRACE, msg, null, t);
    }

    @Override
    public void trace(Supplier<String> message) {
        if (isTraceEnabled()) {
            log(Level.TRACE, message.get(), null, null);
        }
    }

    @Override
    public void debug(String msg) {
        log(Level.DEBUG, msg, null, null);
    }

    @Override
    public void debug(String format, Object... args) {
        log(Level.DEBUG, format, args, null);
    }

    @Override
    public void debug(String msg, Throwable t) {
        log(Level.DEBUG, msg, null, t);
    }

    @Override
    public void debug(Supplier<String> message) {
        if (isDebugEnabled()) {
            log(Level.DEBUG, message.get(), null, null);
        }
    }

    @Override
    public void info(String msg) {
        log(Level.INFO, msg, null, null);
    }

    @Override
    public void info(String format, Object... args) {
        log(Level.INFO, format, args, null);
    }

    @Override
    public void info(String msg, Throwable t) {
        log(Level.INFO, msg, null, t);
    }

    @Override
    public void info(Supplier<String> message) {
        if (isInfoEnabled()) {
            log(Level.INFO, message.get(), null, null);
        }
    }

    @Override
    public void warn(String msg) {
        log(Level.WARN, msg, null, null);
    }

    @Override
    public void warn(String format, Object... args) {
        log(Level.WARN, format, args, null);
    }

    @Override
    public void warn(String msg, Throwable t) {
        log(Level.WARN, msg, null, t);
    }

    @Override
    public void warn(Supplier<String> message) {
        if (isWarnEnabled()) {
            log(Level.WARN, message.get(), null, null);
        }
    }

    @Override
    public void error(String msg) {
        log(Level.ERROR, msg, null, null);
    }

    @Override
    public void error(String format, Object... args) {
        log(Level.ERROR, format, args, null);
    }

    @Override
    public void error(String msg, Throwable t) {
        log(Level.ERROR, msg, null, t);
    }

    @Override
    public void error(Supplier<String> message) {
        if (isErrorEnabled()) {
            log(Level.ERROR, message.get(), null, null);
        }
    }

    @Override
    public void fatal(String msg) {
        log(Level.FATAL, msg, null, null);
    }

    @Override
    public void fatal(String format, Object... args) {
        log(Level.FATAL, format, args, null);
    }

    @Override
    public void fatal(String msg, Throwable t) {
        log(Level.FATAL, msg, null, t);
    }

    @Override
    public void fatal(Supplier<String> message) {
        if (isFatalEnabled()) {
            log(Level.FATAL, message.get(), null, null);
        }
    }


    /**
     * 内部日志记录方法，检查有效级别、格式化消息并分发给所有附加器。
     * <p>
     * 若 {@code args} 的最后一个元素是 {@link Throwable}，则将其作为关联异常剥离，
     * 剩余参数用于占位符填充（与 SLF4J/log4j2 行为一致）。
     * 当存在需要调用位置的附加器布局时，额外捕获调用点堆栈帧。
     *
     * @param level     日志级别
     * @param msg       原始消息或格式模板
     * @param args      格式化参数，为 null 或空时不执行格式化
     * @param throwable 显式关联的异常，可能来自 {@code (String, Throwable)} 重载
     */
    private void log(Level level, String msg, Object[] args, Throwable throwable) {
        Level effective = LoggerFactory.getEffectiveLevel(name);
        if (!effective.isEnabled(level)) {
            return;
        }
        Object[] formatArgs = args;
        if (throwable == null && args != null && args.length > 0
                && args[args.length - 1] instanceof Throwable t) {
            throwable = t;
            formatArgs = Arrays.copyOf(args, args.length - 1);
        }
        String formatted = formatArgs != null && formatArgs.length > 0
                ? MessageFormatter.format(msg, formatArgs)
                : msg;
        if (masker != Masker.NONE) {
            formatted = masker.mask(formatted);
        }
        StackTraceElement caller = needsCallerLocation() ? findCaller() : null;
        LogEvent event = new LogEvent(name, level, msg, formatArgs, formatted, throwable, caller);
        appendLoopOnAppenders(event);
    }


    /**
     * 判断当前日志器（含沿 additivity 链向上的父级）是否有任意附加器的布局需要调用位置信息。
     *
     * @return 如果需要调用位置则返回 true
     */
    private boolean needsCallerLocation() {
        LoggerImpl current = this;
        while (current != null) {
            for (Appender appender : current.appenders) {
                Layout layout = appender.getLayout();
                if (layout != null && layout.requiresCallerLocation()) {
                    return true;
                }
            }
            if (!current.additivity) {
                break;
            }
            current = (LoggerImpl) LoggerFactory.getParent(current.name);
        }
        return false;
    }


    /**
     * 在调用堆栈中查找第一个不属于日志框架包的代码位置，作为日志触发点。
     *
     * @return 调用点堆栈帧，查找失败时返回最底层帧或 null
     */
    private static StackTraceElement findCaller() {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        for (StackTraceElement element : stack) {
            if (!element.getClassName().startsWith("com.flora.runtime.log")) {
                return element;
            }
        }
        return stack.length > 0 ? stack[stack.length - 1] : null;
    }


    /**
     * 将日志事件分发到当前日志器的所有附加器，
     * 并根据 additivity 标志决定是否继续向父级日志器传递。
     *
     * @param event 日志事件
     */
    private void appendLoopOnAppenders(LogEvent event) {
        LoggerImpl current = this;
        while (current != null) {
            for (Appender appender : current.appenders) {
                appender.append(event);
            }
            if (!current.additivity) {
                break;
            }
            current = (LoggerImpl) LoggerFactory.getParent(current.name);
        }
    }
}
