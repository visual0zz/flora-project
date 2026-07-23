package com.flora.java;

/**
 * 参数校验工具类，提供简洁的静态方法用于前置条件检查。
 * <p>
 * 当条件不满足时抛出带有指定错误消息的 {@link IllegalArgumentException}。
 * 所有方法在条件满足时返回被检查的参数，以支持链式调用。
 * </p>
 */
public final class CheckUtil {

    private CheckUtil() {
    }


    public static <T> T notNull(T reference) {
        return notNull(reference, "参数不能为空");
    }
    public static <T> T notNull(T reference, String errorMsg) {
        if (reference == null) {
            throw new NullPointerException(errorMsg);
        }
        return reference;
    }

    public static String notEmpty(String str) {
        return notEmpty(str, "参数不能为空");
    }
    public static String notEmpty(String str, String errorMsg) {
        if (str == null || str.isEmpty()) {
            throw new IllegalArgumentException(errorMsg);
        }
        return str;
    }
    public static String notBlank(String str) {
        return notBlank(str, "参数不能为空");
    }
    public static String notBlank(String str, String errorMsg) {
        if (str == null || str.isBlank()) {
            throw new IllegalArgumentException(errorMsg);
        }
        return str;
    }

    public static void mustTrue(boolean expression, String errorMsg) {
        if (!expression) {
            throw new IllegalArgumentException(errorMsg);
        }
    }
}
