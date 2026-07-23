package com.flora.log;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * 日志记录器工厂，管理和提供 Logger 实例。
 * <p>
 * 默认创建一个名为 "root" 的根日志器。
 * 日志器名称按层级组织（以点分隔），支持父子级别联。
 */
public final class LoggerFactory {

    private LoggerFactory() {
    }

    private static final Map<String, LoggerImpl> LOGGER_MAP = new ConcurrentHashMap<>();

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
        LoggerImpl existing = LOGGER_MAP.get(name);
        if (existing != null) {
            return existing;
        }
        LoggerImpl logger = new LoggerImpl(name);
        LoggerImpl old = LOGGER_MAP.putIfAbsent(name, logger);
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
    static LoggerImpl getRootLogger() {
        LoggerImpl root = LOGGER_MAP.get("root");
        assert root != null;
        return root;
    }

    
    /**
     * 根据名称查找父级日志器。
     * <p>
     * 通过去除名称中最后一个点之后的部分得到父级名称，递归查找。
     *
     * @param name 日志器名称
     * @return 父级日志器实例，如果没有父级则返回 null
     */
    static LoggerImpl getParent(String name) {
        if (name == null || name.isEmpty() || "root".equals(name)) {
            return null;
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            String parentName = name.substring(0, dot);
            LoggerImpl parent = LOGGER_MAP.get(parentName);
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
    static Level getEffectiveLevel(String name) {
        LoggerImpl logger = LOGGER_MAP.get(name);
        if (logger != null && logger.getLevel() != null) {
            return logger.getLevel();
        }
        
        LoggerImpl parent = getParent(name);
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
     * @return 名称到 LoggerImpl 的映射（只读视图）
     */
    public static Map<String, LoggerImpl> getLoggerMap() {
        return LOGGER_MAP;
    }

    
    /**
     * 重置所有日志器，清空注册表并重新创建根日志器。
     */
    public static void reset() {
        LOGGER_MAP.clear();
        LOGGER_MAP.put("root", new LoggerImpl("root"));
    }
}
