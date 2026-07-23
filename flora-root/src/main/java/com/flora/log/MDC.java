package com.flora.log;

import java.util.HashMap;
import java.util.Map;


/**
 * 映射诊断上下文（Mapped Diagnostic Context），基于 ThreadLocal 为每个线程
 * 提供独立的键值对上下文。常用于在多线程日志中关联同一请求的上下文信息。
 */
public final class MDC {

    private static final ThreadLocal<Map<String, String>> CONTEXT =
            ThreadLocal.withInitial(HashMap::new);

    private MDC() {
    }

    /**
     * 向当前线程的上下文中存入一个键值对。
     *
     * @param key   键
     * @param value 值
     */
    public static void put(String key, String value) {
        CONTEXT.get().put(key, value);
    }

    /**
     * 获取当前线程上下文中指定键的值。
     *
     * @param key 键
     * @return 对应的值，不存在则返回 null
     */
    public static String get(String key) {
        return CONTEXT.get().get(key);
    }

    /**
     * 移除当前线程上下文中指定键的映射。
     *
     * @param key 要移除的键
     */
    public static void remove(String key) {
        CONTEXT.get().remove(key);
    }

    /**
     * 清空当前线程的整个上下文。
     */
    public static void clear() {
        CONTEXT.get().clear();
    }

    /**
     * 返回当前线程上下文的快照副本。
     *
     * @return 当前上下文的一个新副本
     */
    public static Map<String, String> copy() {
        return new HashMap<>(CONTEXT.get());
    }
}
