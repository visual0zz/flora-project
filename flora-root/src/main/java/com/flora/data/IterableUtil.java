package com.flora.data;

import com.flora.container.CollectionUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 可迭代对象（Iterable）的工具类。
 * <p>提供对 Iterable 的空值安全判断、集合转换、过滤、映射及元素获取等便捷操作。</p>
 */
public final class IterableUtil {

    private IterableUtil() {
    }

    /**
     * 判断 Iterable 是否为空（null 或不含元素）。
     *
     * @param iterable 待检查的 Iterable
     * @return 如果为 null 或不含元素则返回 true
     */
    public static boolean isEmpty(Iterable<?> iterable) {
        if (iterable == null) {
            return true;
        }
        if (iterable instanceof Collection<?> coll) {
            return CollectionUtil.isEmpty(coll);
        }
        return !iterable.iterator().hasNext();
    }

    /**
     * 判断 Iterable 是否为非空（不为 null 且至少含一个元素）。
     *
     * @param iterable 待检查的 Iterable
     * @return 如果不为 null 且至少含一个元素则返回 true
     */
    public static boolean isNotEmpty(Iterable<?> iterable) {
        return !isEmpty(iterable);
    }

    /**
     * 计算 Iterable 的元素数量。
     * <p>如果 Iterable 实现 Collection 接口则直接调用 size()，否则通过迭代计数。</p>
     *
     * @param iterable 待计算的 Iterable
     * @return 元素数量，null 返回 0
     */
    public static int size(Iterable<?> iterable) {
        if (iterable == null) {
            return 0;
        }
        if (iterable instanceof java.util.Collection<?> coll) {
            return coll.size();
        }
        int count = 0;
        for (var ignored : iterable) {
            count++;
        }
        return count;
    }

    /**
     * 将 Iterable 转换为 List。
     *
     * @param iterable 待转换的 Iterable，可以为 null
     * @param <T>      元素类型
     * @return 包含所有元素的新 List，null 返回空列表
     */
    public static <T> List<T> toList(Iterable<T> iterable) {
        if (iterable == null) {
            return List.of();
        }
        if (iterable instanceof java.util.Collection<T> coll) {
            return new ArrayList<>(coll);
        }
        List<T> list = new ArrayList<>();
        for (T item : iterable) {
            list.add(item);
        }
        return list;
    }

    /**
     * 将 Iterable 转换为 Set。
     *
     * @param iterable 待转换的 Iterable，可以为 null
     * @param <T>      元素类型
     * @return 包含所有元素的新 HashSet，null 返回空 Set
     */
    public static <T> Set<T> toSet(Iterable<T> iterable) {
        if (iterable == null) {
            return Set.of();
        }
        if (iterable instanceof java.util.Collection<T> coll) {
            return new HashSet<>(coll);
        }
        Set<T> set = new HashSet<>();
        for (T item : iterable) {
            set.add(item);
        }
        return set;
    }

    /**
     * 根据谓词过滤 Iterable 中的元素。
     *
     * @param iterable  待过滤的 Iterable，可以为 null
     * @param predicate 过滤条件，返回 true 的元素会被保留
     * @param <T>       元素类型
     * @return 过滤后的元素列表
     */
    public static <T> List<T> filter(Iterable<T> iterable, Predicate<? super T> predicate) {
        if (iterable == null) {
            return List.of();
        }
        if (iterable instanceof Collection<T> coll) {
            return CollectionUtil.filter(coll, predicate);
        }
        List<T> result = new ArrayList<>();
        for (T item : iterable) {
            if (predicate.test(item)) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * 对 Iterable 中的每个元素执行映射转换。
     *
     * @param iterable 待映射的 Iterable，可以为 null
     * @param mapper   转换函数
     * @param <T>      输入元素类型
     * @param <R>      输出元素类型
     * @return 转换后的元素列表
     */
    public static <T, R> List<R> map(Iterable<T> iterable, Function<? super T, ? extends R> mapper) {
        if (iterable == null) {
            return List.of();
        }
        if (iterable instanceof Collection<T> coll) {
            return CollectionUtil.map(coll, mapper);
        }
        List<R> result = new ArrayList<>();
        for (T item : iterable) {
            result.add(mapper.apply(item));
        }
        return result;
    }

    /**
     * 获取 Iterable 中的第一个元素。
     *
     * @param iterable 待获取的 Iterable，可以为 null
     * @param <T>      元素类型
     * @return 第一个元素，如果为空或 null 则返回 null
     */
    public static <T> T first(Iterable<T> iterable) {
        if (iterable == null) {
            return null;
        }
        if (iterable instanceof Collection<T> coll) {
            return CollectionUtil.first(coll);
        }
        var it = iterable.iterator();
        return it.hasNext() ? it.next() : null;
    }
}
