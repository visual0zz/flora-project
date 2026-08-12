package com.flora.root.java;
import com.flora.root.tag.ModuleEntry;

/**
 * 参数校验工具类，提供简洁的静态方法用于前置条件检查。
 * <p>
 * 当条件不满足时抛出带有指定错误消息的 {@link IllegalArgumentException}。
 * 所有方法在条件满足时返回被检查的参数，以支持链式调用。
 * </p>
 */
@ModuleEntry
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

    /**
     * 校验对象是指定类型的实例，返回强转后的对象以支持链式使用。
     *
     * @param obj  待校验的对象
     * @param type 期望的类型
     * @param <T>  期望的类型
     * @return 强转后的对象
     * @throws IllegalArgumentException 若 obj 为 null 或不是该类型的实例
     */
    public static <T> T isInstanceOf(Object obj, Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("期望类型不能为空");
        }
        return isInstanceOf(obj, type, "对象不是 " + type.getName() + " 的实例: " + obj);
    }

    /**
     * 校验对象是指定类型的实例，返回强转后的对象以支持链式使用。
     *
     * @param obj      待校验的对象
     * @param type     期望的类型
     * @param errorMsg 校验失败的错误消息
     * @param <T>      期望的类型
     * @return 强转后的对象
     * @throws IllegalArgumentException 若 obj 为 null 或不是该类型的实例
     */
    public static <T> T isInstanceOf(Object obj, Class<T> type, String errorMsg) {
        if (type == null) {
            throw new IllegalArgumentException("期望类型不能为空");
        }
        if (!type.isInstance(obj)) {
            throw new IllegalArgumentException(errorMsg);
        }
        return type.cast(obj);
    }

    /**
     * 校验两个对象相等（基于 {@link java.util.Objects#equals}），不相等时抛出 {@link IllegalArgumentException}。
     *
     * @param a 对象 a
     * @param b 对象 b
     * @throws IllegalArgumentException 若两者不相等
     */
    public static void areEqual(Object a, Object b) {
        areEqual(a, b, "对象不相等: " + a + " != " + b);
    }

    /**
     * 校验两个对象相等（基于 {@link java.util.Objects#equals}），不相等时抛出 {@link IllegalArgumentException}。
     *
     * @param a        对象 a
     * @param b        对象 b
     * @param errorMsg 校验失败的错误消息
     * @throws IllegalArgumentException 若两者不相等
     */
    public static void areEqual(Object a, Object b, String errorMsg) {
        if (!java.util.Objects.equals(a, b)) {
            throw new IllegalArgumentException(errorMsg);
        }
    }

    /**
     * 校验对象状态合法，不成立时抛出 {@link IllegalStateException}（区别于参数校验的
     * {@link IllegalArgumentException}，用于对象内部状态或生命周期不合法的情形）。
     *
     * @param expression 状态是否合法
     * @param errorMsg   不合法时的错误消息
     * @throws IllegalStateException 若 expression 为 false
     */
    public static void state(boolean expression, String errorMsg) {
        if (!expression) {
            throw new IllegalStateException(errorMsg);
        }
    }
}
