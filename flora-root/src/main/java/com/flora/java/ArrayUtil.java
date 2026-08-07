package com.flora.java;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.flora.tag.ModuleEntry;

/**
 * 数组处理工具类，提供判空、包含判断、索引查找、子数组截取、转换为列表及数组合并等常用操作。
 * <p>除 {@link #isEmpty(Object)} / {@link #isNotEmpty(Object)} 通过反射统一支持原始类型数组外，
 * 其余方法针对对象数组。所有方法均为 null 安全。</p>
 */
@ModuleEntry
public final class ArrayUtil {

    private ArrayUtil() {
    }

    // ==================== 判空 ====================

    /**
     * 判断数组是否为 null 或长度为 0（通过反射支持原始类型数组）。
     *
     * @param array 待检查的数组，可为 null
     * @return 若为 null 或空数组则返回 true
     * @throws IllegalArgumentException 若参数非数组类型
     */
    public static boolean isEmpty(Object array) {
        if (array == null) {
            return true;
        }
        if (!array.getClass().isArray()) {
            throw new IllegalArgumentException("参数不是数组: " + array.getClass().getName());
        }
        return Array.getLength(array) == 0;
    }

    /**
     * 判断数组是否非空（通过反射支持原始类型数组）。
     *
     * @param array 待检查的数组，可为 null
     * @return 若不为 null 且长度大于 0 则返回 true
     * @throws IllegalArgumentException 若参数非数组类型
     */
    public static boolean isNotEmpty(Object array) {
        return !isEmpty(array);
    }

    // ==================== 包含 / 索引 ====================

    /**
     * 判断对象数组中是否包含指定元素（null 安全，基于 equals 比较）。
     *
     * @param array   待搜索的数组，可为 null
     * @param element 待查找的元素
     * @return 若包含则返回 true
     */
    public static boolean contains(Object[] array, Object element) {
        return indexOf(array, element) >= 0;
    }

    /**
     * 查找元素在对象数组中的首次出现位置（null 安全，基于 equals 比较）。
     *
     * @param array   待搜索的数组，可为 null
     * @param element 待查找的元素
     * @return 索引；未找到或数组为 null 时返回 -1
     */
    public static int indexOf(Object[] array, Object element) {
        if (array == null) {
            return -1;
        }
        for (int i = 0; i < array.length; i++) {
            if (ObjectUtil.equals(array[i], element)) {
                return i;
            }
        }
        return -1;
    }

    // ==================== 子数组 ====================

    /**
     * 截取数组的 [start, end) 区间（左闭右开），自动夹紧越界索引。
     * <p>负数索引按从末尾倒数解释（如 -1 表示末尾）。</p>
     *
     * @param array 源数组，可为 null（返回 null）
     * @param start 起始索引（含）
     * @param end   结束索引（不含）
     * @param <T>   数组元素类型
     * @return 截取得到的新数组
     */
    public static <T> T[] subarray(T[] array, int start, int end) {
        if (array == null) {
            return null;
        }
        int len = array.length;
        int s = start < 0 ? Math.max(len + start, 0) : Math.min(start, len);
        int e = end < 0 ? len + end : Math.min(end, len);
        if (e < s) {
            e = s;
        }
        int newLen = e - s;
        Class<?> component = array.getClass().getComponentType();
        @SuppressWarnings("unchecked")
        T[] result = (T[]) Array.newInstance(component, newLen);
        System.arraycopy(array, s, result, 0, newLen);
        return result;
    }

    // ==================== 转列表 ====================

    /**
     * 将对象数组转换为可变的 {@link ArrayList}（防御性拷贝，与原数组互不影响）。
     *
     * @param array 源数组，可为 null（返回空列表）
     * @param <T>   数组元素类型
     * @return 包含数组全部元素的列表
     */
    public static <T> List<T> toList(T[] array) {
        if (array == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(array));
    }

    // ==================== 合并 ====================

    /**
     * 合并多个对象数组为一个新数组，按参数顺序拼接。null 数组被跳过。
     *
     * @param arrays 待合并的数组（可变参数），可为 null
     * @param <T>    数组元素类型
     * @return 合并后的新数组；所有入参均为 null 时返回 null
     */
    @SafeVarargs
    public static <T> T[] concat(T[]... arrays) {
        if (arrays == null) {
            return null;
        }
        int total = 0;
        Class<?> component = null;
        for (T[] a : arrays) {
            if (a != null) {
                total += a.length;
                if (component == null) {
                    component = a.getClass().getComponentType();
                }
            }
        }
        if (component == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        T[] result = (T[]) Array.newInstance(component, total);
        int pos = 0;
        for (T[] a : arrays) {
            if (a != null) {
                System.arraycopy(a, 0, result, pos, a.length);
                pos += a.length;
            }
        }
        return result;
    }
}
