package com.flora.java;

import java.util.HashSet;
import java.util.Set;

/**
 * 异常处理工具类，提供异常包装、根因获取、安全取消息及因果链匹配等常用操作。
 */
public final class ExceptionUtil {

    private ExceptionUtil() {
    }

    // ==================== 包装 ====================

    /**
     * 将受检异常包装为 {@link RuntimeException}，保留原始异常作为 cause。
     *
     * @param cause   原始异常，不能为 null
     * @param message 包装后的异常消息
     * @return 包装后的 RuntimeException
     */
    public static RuntimeException wrap(Throwable cause, String message) {
        CheckUtil.notNull(cause, "原始异常不能为空");
        return new RuntimeException(message, cause);
    }

    /**
     * 将受检异常包装为 {@link RuntimeException}，沿用原始异常的消息。
     *
     * @param cause 原始异常，不能为 null
     * @return 包装后的 RuntimeException
     */
    public static RuntimeException wrap(Throwable cause) {
        CheckUtil.notNull(cause, "原始异常不能为空");
        return new RuntimeException(cause.getMessage(), cause);
    }

    // ==================== 根因 ====================

    /**
     * 获取异常链最底层的根因（沿 cause 链向下，直到无可追溯的原因为止）。
     * <p>若异常自身无 cause，则返回自身；输入为 null 时返回 null。</p>
     *
     * @param throwable 异常
     * @return 根因异常
     */
    public static Throwable getRootCause(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        Set<Throwable> seen = new HashSet<>();
        Throwable current = throwable;
        while (current.getCause() != null && seen.add(current)) {
            current = current.getCause();
        }
        return current;
    }

    // ==================== 安全取消息 ====================

    /**
     * 安全获取异常消息（null 安全）。
     *
     * @param throwable 异常
     * @return 异常消息；异常为 null 时返回 null
     */
    public static String getMessage(Throwable throwable) {
        return throwable == null ? null : throwable.getMessage();
    }

    // ==================== 因果链匹配 ====================

    /**
     * 判断异常链（含自身）中是否存在指定类型的异常。
     *
     * @param throwable  异常，可为 null
     * @param causeType  目标异常类型
     * @param <T>        目标异常类型
     * @return 若存在匹配类型的异常则返回 true
     */
    public static <T extends Throwable> boolean isCausedBy(Throwable throwable, Class<T> causeType) {
        CheckUtil.notNull(causeType, "目标异常类型不能为空");
        Set<Throwable> seen = new HashSet<>();
        Throwable current = throwable;
        while (current != null && seen.add(current)) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
