package com.flora.log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Logger 接口的默认实现。
 * <p>
 * 维护日志级别、附加器列表和层级追加功能（additivity）。
 * 通过 LoggerFactory 获取有效级别，并支持向父级日志器传递日志事件。
 */
public final class LoggerImpl implements Logger {

    private final String name;
    private Level level;               
    private boolean additivity = true;
    private final List<Appender> appenders = Collections.synchronizedList(new ArrayList<>());

    
    volatile Level effectiveLevel;

    LoggerImpl(String name) {
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
    void recomputeEffectiveLevel() {
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
    public void trace(String msg) {
        log(Level.TRACE, msg, null);
    }

    @Override
    public void trace(String format, Object... args) {
        log(Level.TRACE, format, args);
    }

    @Override
    public void debug(String msg) {
        log(Level.DEBUG, msg, null);
    }

    @Override
    public void debug(String format, Object... args) {
        log(Level.DEBUG, format, args);
    }

    @Override
    public void info(String msg) {
        log(Level.INFO, msg, null);
    }

    @Override
    public void info(String format, Object... args) {
        log(Level.INFO, format, args);
    }

    @Override
    public void warn(String msg) {
        log(Level.WARN, msg, null);
    }

    @Override
    public void warn(String format, Object... args) {
        log(Level.WARN, format, args);
    }

    @Override
    public void error(String msg) {
        log(Level.ERROR, msg, null);
    }

    @Override
    public void error(String format, Object... args) {
        log(Level.ERROR, format, args);
    }

    

    /**
     * 内部日志记录方法，检查有效级别、格式化消息并分发给所有附加器。
     *
     * @param level 日志级别
     * @param msg   原始消息或格式模板
     * @param args  格式化参数，为 null 或空时不执行格式化
     */
    private void log(Level level, String msg, Object[] args) {
        Level effective = LoggerFactory.getEffectiveLevel(name);
        if (!effective.isEnabled(level)) {
            return;
        }
        String formatted = args != null && args.length > 0
                ? MessageFormatter.format(msg, args)
                : msg;
        LogEvent event = new LogEvent(name, level, msg, args, formatted);
        appendLoopOnAppenders(event);
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
            current = LoggerFactory.getParent(current.name);
        }
    }
}
