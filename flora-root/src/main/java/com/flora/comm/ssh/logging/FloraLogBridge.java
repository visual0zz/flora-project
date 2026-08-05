package com.flora.comm.ssh.logging;

import com.flora.comm.ssh.Logger;
import com.flora.runtime.log.LoggerFactory;

/**
 * 将 {@link Logger}（通信层日志接口）桥接到 {@code com.flora.runtime.log} 的统一日志门面。
 * <p>级别映射：DEBUG→DEBUG、INFO→INFO、WARN→WARN、ERROR/FATAL→ERROR。</p>
 */
public final class FloraLogBridge implements Logger {

    private final com.flora.runtime.log.Logger delegate;

    public FloraLogBridge(com.flora.runtime.log.Logger delegate) {
        this.delegate = delegate;
    }

    public static FloraLogBridge forName(String name) {
        return new FloraLogBridge(LoggerFactory.getLogger(name));
    }

    @Override
    public boolean isEnabled(int level) {
        return switch (level) {
            case DEBUG -> delegate.isDebugEnabled();
            case INFO -> delegate.isInfoEnabled();
            case WARN -> delegate.isWarnEnabled();
            case ERROR, FATAL -> delegate.isErrorEnabled();
            default -> delegate.isErrorEnabled();
        };
    }

    @Override
    public void log(int level, String message) {
        switch (level) {
            case DEBUG -> delegate.debug(message);
            case INFO -> delegate.info(message);
            case WARN -> delegate.warn(message);
            case ERROR, FATAL -> delegate.error(message);
            default -> delegate.error(message);
        };
    }
}
