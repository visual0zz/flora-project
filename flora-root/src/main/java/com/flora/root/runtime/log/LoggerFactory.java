package com.flora.root.runtime.log;

import com.flora.root.tag.ModuleEntry;
import com.flora.root.runtime.log.impl.LoggerImpl;
import com.flora.root.runtime.log.spi.Masker;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;


/**
 * 日志记录器工厂，管理和提供 Logger 实例。
 * <p>
 * 默认创建一个名为 "root" 的根日志器。
 * 日志器名称按层级组织（以点分隔），支持父子级别联。
 */
@ModuleEntry
public final class LoggerFactory {

    private LoggerFactory() {
    }

    private static final Map<String, Logger> LOGGER_MAP = new ConcurrentHashMap<>();

    /**
     * 全局默认脱敏器，新建日志器与 {@link #setDefaultMasker(Masker)} 都会应用它。
     * 默认 {@link Masker#NONE}，即不脱敏。
     */
    private static volatile Masker defaultMasker = Masker.NONE;

    static {

        LOGGER_MAP.put("root", new LoggerImpl("root"));
    }




    /**
     * 根据名称获取或创建一个 Logger 实例。
     * <p>
     * 如果已存在同名日志器则直接返回，否则创建新实例并重新计算有效级别。
     *
     * @param name 日志器名称（支持点分隔的层级名称）
     * @return Logger 实例
     */
    public static Logger getLogger(String name) {
        LoggerImpl existing = (LoggerImpl) LOGGER_MAP.get(name);
        if (existing != null) {
            return existing;
        }
        LoggerImpl logger = new LoggerImpl(name);
        LoggerImpl old = (LoggerImpl) LOGGER_MAP.putIfAbsent(name, logger);
        if (old != null) {
            return old;
        }

        logger.recomputeEffectiveLevel();
        return logger;
    }


    /**
     * 根据 Class 对象获取 Logger 实例，使用类的全限定名作为日志器名称。
     *
     * @param clazz 目标类
     * @return Logger 实例
     */
    public static Logger getLogger(Class<?> clazz) {
        return getLogger(clazz.getName());
    }




    /**
     * 获取根日志器。
     *
     * @return 根日志器实例
     */
    public static Logger getRootLogger() {
        return LOGGER_MAP.get("root");
    }

    /**
     * 获取静默（no-op）日志器：所有方法均为空实现，不产生任何输出。
     * <p>用于「调用方未注入真实日志器」的默认路径（例如独立复用的解析器/工具），
     * 避免在无日志配置环境下报错或产生噪音；需要输出时由调用方显式传入真实日志器。</p>
     *
     * @return 单例静默日志器，不会为 null
     */
    public static Logger noOp() {
        return NO_OP;
    }


    /**
     * 根据名称查找父级日志器。
     * <p>
     * 通过去除名称中最后一个点之后的部分得到父级名称，递归查找。
     *
     * @param name 日志器名称
     * @return 父级日志器实例，如果没有父级则返回 null
     */
    public static Logger getParent(String name) {
        if (name == null || name.isEmpty() || "root".equals(name)) {
            return null;
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            String parentName = name.substring(0, dot);
            LoggerImpl parent = (LoggerImpl) LOGGER_MAP.get(parentName);
            if (parent != null) {
                return parent;
            }

            return getParent(parentName);
        }
        return LOGGER_MAP.get("root");
    }


    /**
     * 获取指定名称日志器的有效级别。
     * <p>
     * 如果日志器自身没有设置级别，则递归查找父级级别，最终默认返回 {@link Level#DEBUG}。
     *
     * @param name 日志器名称
     * @return 有效日志级别
     */
    public static Level getEffectiveLevel(String name) {
        LoggerImpl logger = (LoggerImpl) LOGGER_MAP.get(name);
        if (logger != null && logger.getLevel() != null) {
            return logger.getLevel();
        }

        LoggerImpl parent = (LoggerImpl) getParent(name);
        if (parent != null) {
            if (parent.getLevel() != null) {
                return parent.getLevel();
            }
            return getEffectiveLevel(parent.getName());
        }
        return Level.DEBUG;
    }




    /**
     * 获取所有已注册的日志器映射表。
     *
     * @return 名称到 Logger 的映射（只读视图）
     */
    public static Map<String, Logger> getLoggerMap() {
        return LOGGER_MAP;
    }


    /**
     * 重置所有日志器，清空注册表并重新创建根日志器。
     */
    public static void reset() {
        LOGGER_MAP.clear();
        LOGGER_MAP.put("root", new LoggerImpl("root"));
    }

    /**
     * 获取全局默认脱敏器。
     *
     * @return 当前默认脱敏器，不会为 null
     */
    public static Masker defaultMasker() {
        return defaultMasker;
    }

    /**
     * 设置全局默认脱敏器，并立即应用到所有已注册的日志器。
     * 后续新建的日志器也会继承该默认值。
     *
     * @param masker 脱敏器，不允许为 null
     */
    public static void setDefaultMasker(Masker masker) {
        defaultMasker = masker != null ? masker : Masker.NONE;
        for (Logger logger : LOGGER_MAP.values()) {
            ((LoggerImpl) logger).setMasker(defaultMasker);
        }
    }

    /** 静默日志器单例：所有方法空实现。 */
    private static final Logger NO_OP = new Logger() {
        @Override public String getName() { return "noop"; }

        @Override public boolean isTraceEnabled() { return false; }
        @Override public boolean isDebugEnabled() { return false; }
        @Override public boolean isInfoEnabled() { return false; }
        @Override public boolean isWarnEnabled() { return false; }
        @Override public boolean isErrorEnabled() { return false; }
        @Override public boolean isFatalEnabled() { return false; }

        @Override public void trace(String msg) {}
        @Override public void trace(String format, Object... args) {}
        @Override public void trace(String msg, Throwable t) {}
        @Override public void trace(Supplier<String> message) {}

        @Override public void debug(String msg) {}
        @Override public void debug(String format, Object... args) {}
        @Override public void debug(String msg, Throwable t) {}
        @Override public void debug(Supplier<String> message) {}

        @Override public void info(String msg) {}
        @Override public void info(String format, Object... args) {}
        @Override public void info(String msg, Throwable t) {}
        @Override public void info(Supplier<String> message) {}

        @Override public void warn(String msg) {}
        @Override public void warn(String format, Object... args) {}
        @Override public void warn(String msg, Throwable t) {}
        @Override public void warn(Supplier<String> message) {}

        @Override public void error(String msg) {}
        @Override public void error(String format, Object... args) {}
        @Override public void error(String msg, Throwable t) {}
        @Override public void error(Supplier<String> message) {}

        @Override public void fatal(String msg) {}
        @Override public void fatal(String format, Object... args) {}
        @Override public void fatal(String msg, Throwable t) {}
        @Override public void fatal(Supplier<String> message) {}
    };
}
