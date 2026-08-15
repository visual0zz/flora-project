package com.flora.shell;

import com.flora.root.runtime.log.Logger;

import java.util.function.Supplier;

/**
 * 命令级日志装饰器：在底层 {@link Logger} 的每条消息前加 {@code [命令名]} 标注。
 * <p>由 {@link CommandService} 为每次调用基于命令名构造，经 {@link Invocation#log()}
 * 交给命令记录内部过程；日志级别判断与底层 logger 一致。</p>
 */
final class CommandLogger implements Logger {

    private final Logger delegate;
    private final String prefix;

    CommandLogger(Logger delegate, String commandName) {
        this.delegate = delegate;
        this.prefix = "[" + commandName + "] ";
    }

    private String tag(String msg) {
        return prefix + msg;
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public boolean isTraceEnabled() {
        return delegate.isTraceEnabled();
    }

    @Override
    public void trace(String msg) {
        delegate.trace(tag(msg));
    }

    @Override
    public void trace(String format, Object... args) {
        delegate.trace(prefix + format, args);
    }

    @Override
    public void trace(String msg, Throwable t) {
        delegate.trace(tag(msg), t);
    }

    @Override
    public void trace(Supplier<String> message) {
        delegate.trace(() -> tag(message.get()));
    }

    @Override
    public boolean isDebugEnabled() {
        return delegate.isDebugEnabled();
    }

    @Override
    public void debug(String msg) {
        delegate.debug(tag(msg));
    }

    @Override
    public void debug(String format, Object... args) {
        delegate.debug(prefix + format, args);
    }

    @Override
    public void debug(String msg, Throwable t) {
        delegate.debug(tag(msg), t);
    }

    @Override
    public void debug(Supplier<String> message) {
        delegate.debug(() -> tag(message.get()));
    }

    @Override
    public boolean isInfoEnabled() {
        return delegate.isInfoEnabled();
    }

    @Override
    public void info(String msg) {
        delegate.info(tag(msg));
    }

    @Override
    public void info(String format, Object... args) {
        delegate.info(prefix + format, args);
    }

    @Override
    public void info(String msg, Throwable t) {
        delegate.info(tag(msg), t);
    }

    @Override
    public void info(Supplier<String> message) {
        delegate.info(() -> tag(message.get()));
    }

    @Override
    public boolean isWarnEnabled() {
        return delegate.isWarnEnabled();
    }

    @Override
    public void warn(String msg) {
        delegate.warn(tag(msg));
    }

    @Override
    public void warn(String format, Object... args) {
        delegate.warn(prefix + format, args);
    }

    @Override
    public void warn(String msg, Throwable t) {
        delegate.warn(tag(msg), t);
    }

    @Override
    public void warn(Supplier<String> message) {
        delegate.warn(() -> tag(message.get()));
    }

    @Override
    public boolean isErrorEnabled() {
        return delegate.isErrorEnabled();
    }

    @Override
    public void error(String msg) {
        delegate.error(tag(msg));
    }

    @Override
    public void error(String format, Object... args) {
        delegate.error(prefix + format, args);
    }

    @Override
    public void error(String msg, Throwable t) {
        delegate.error(tag(msg), t);
    }

    @Override
    public void error(Supplier<String> message) {
        delegate.error(() -> tag(message.get()));
    }

    @Override
    public boolean isFatalEnabled() {
        return delegate.isFatalEnabled();
    }

    @Override
    public void fatal(String msg) {
        delegate.fatal(tag(msg));
    }

    @Override
    public void fatal(String format, Object... args) {
        delegate.fatal(prefix + format, args);
    }

    @Override
    public void fatal(String msg, Throwable t) {
        delegate.fatal(tag(msg), t);
    }

    @Override
    public void fatal(Supplier<String> message) {
        delegate.fatal(() -> tag(message.get()));
    }
}
