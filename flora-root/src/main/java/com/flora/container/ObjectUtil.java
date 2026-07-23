package com.flora.container;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * 对象操作工具类，提供判空、比较、toString、默认值等静态方法。
 */
public final class ObjectUtil {

    private ObjectUtil() {
    }

    /**
     * 判断对象是否为 null。
     *
     * @param obj 待检查的对象
     * @return 如果对象为 null，返回 true
     */
    public static boolean isNull(Object obj) {
        return obj == null;
    }

    /**
     * 判断对象是否不为 null。
     *
     * @param obj 待检查的对象
     * @return 如果对象不为 null，返回 true
     */
    public static boolean nonNull(Object obj) {
        return obj != null;
    }

    /**
     * 比较两个对象是否相等（equals），处理 null 安全。
     *
     * @param a 对象 a
     * @param b 对象 b
     * @return 如果相等返回 true
     */
    public static boolean equals(Object a, Object b) {
        return Objects.equals(a, b);
    }

    /**
     * 比较两个对象是否引用同一对象（==）。
     *
     * @param a 对象 a
     * @param b 对象 b
     * @return 如果引用同一对象返回 true
     */
    public static boolean identityEquals(Object a, Object b) {
        return a == b;
    }

    /**
     * 获取对象的 hashCode，对象为 null 时返回 0。
     *
     * @param obj 目标对象
     * @return hashCode 值
     */
    public static int hashCode(Object obj) {
        return Objects.hashCode(obj);
    }

    /**
     * 将对象转为字符串（String.valueOf）。
     *
     * @param obj 目标对象
     * @return 字符串表示
     */
    public static String toString(Object obj) {
        return String.valueOf(obj);
    }

    /**
     * 将对象转为字符串，对象为 null 时返回默认字符串。
     *
     * @param obj        目标对象
     * @param defaultStr 默认字符串
     * @return 字符串表示
     */
    public static String toString(Object obj, String defaultStr) {
        return obj != null ? obj.toString() : defaultStr;
    }

    /**
     * 对象为 null 时返回默认值。
     *
     * @param obj        目标对象
     * @param defaultObj 默认值
     * @param <T>        类型
     * @return 非 null 对象或默认值
     */
    public static <T> T defaultIfNull(T obj, T defaultObj) {
        return obj != null ? obj : defaultObj;
    }

    /**
     * 对象为 null 时通过延迟求值提供默认值。
     *
     * @param obj             目标对象
     * @param defaultSupplier 默认值提供器
     * @param <T>             类型
     * @return 非 null 对象或延迟求值的默认值
     */
    public static <T> T defaultIfNull(T obj, Supplier<T> defaultSupplier) {
        return obj != null ? obj : defaultSupplier.get();
    }

    /**
     * 比较两个 Comparable 对象的大小，处理 null 安全（null 小于非 null）。
     *
     * @param a  对象 a
     * @param b  对象 b
     * @param <T> Comparable 类型
     * @return a &lt; b 返回负数，相等返回 0，a &gt; b 返回正数
     */
    public static <T extends Comparable<? super T>> int compare(T a, T b) {
        if (a == b) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }
        return a.compareTo(b);
    }

    /**
     * 将对象包装为 Supplier。
     *
     * @param obj 目标对象
     * @param <T> 类型
     * @return 返回该对象的 Supplier
     */
    public static <T> Supplier<T> toSupplier(T obj) {
        return () -> obj;
    }
}
