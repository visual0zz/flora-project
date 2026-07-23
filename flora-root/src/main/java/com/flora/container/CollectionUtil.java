package com.flora.container;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 集合操作工具类，提供集合的判空、大小获取、过滤、映射、集合运算等静态方法。
 */
public final class CollectionUtil {

    private CollectionUtil() {
    }

    /**
     * 判断集合是否为空或 null。
     *
     * @param coll 待检查的集合
     * @return 如果集合为 null 或空，返回 true
     */
    public static boolean isEmpty(Collection<?> coll) {
        return coll == null || coll.isEmpty();
    }

    /**
     * 判断集合是否不为空且不为 null。
     *
     * @param coll 待检查的集合
     * @return 如果集合不为 null 且非空，返回 true
     */
    public static boolean isNotEmpty(Collection<?> coll) {
        return !isEmpty(coll);
    }

    /**
     * 判断 Map 是否为空或 null。
     *
     * @param map 待检查的 Map
     * @return 如果 Map 为 null 或空，返回 true
     */
    public static boolean isEmpty(Map<?, ?> map) {
        return map == null || map.isEmpty();
    }

    /**
     * 判断 Map 是否不为空且不为 null。
     *
     * @param map 待检查的 Map
     * @return 如果 Map 不为 null 且非空，返回 true
     */
    public static boolean isNotEmpty(Map<?, ?> map) {
        return !isEmpty(map);
    }

    /**
     * 获取集合的大小，集合为 null 时返回 0。
     *
     * @param coll 目标集合
     * @return 集合大小，null 时返回 0
     */
    public static int size(Collection<?> coll) {
        return coll == null ? 0 : coll.size();
    }

    /**
     * 获取 Map 的大小，Map 为 null 时返回 0。
     *
     * @param map 目标 Map
     * @return Map 大小，null 时返回 0
     */
    public static int size(Map<?, ?> map) {
        return map == null ? 0 : map.size();
    }

    

    /**
     * 检查集合中是否包含任意一个指定元素。
     *
     * @param coll     目标集合
     * @param elements 待检查的元素数组
     * @param <T>      元素类型
     * @return 如果集合包含任意一个指定元素，返回 true
     */
    @SafeVarargs
    public static <T> boolean containsAny(Collection<T> coll, T... elements) {
        if (isEmpty(coll) || elements == null) {
            return false;
        }
        for (T elem : elements) {
            if (coll.contains(elem)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查集合中是否包含所有指定元素。
     *
     * @param coll     目标集合
     * @param elements 待检查的元素数组
     * @param <T>      元素类型
     * @return 如果集合包含所有指定元素，返回 true
     */
    @SafeVarargs
    public static <T> boolean containsAll(Collection<T> coll, T... elements) {
        if (isEmpty(coll) || elements == null) {
            return false;
        }
        for (T elem : elements) {
            if (!coll.contains(elem)) {
                return false;
            }
        }
        return true;
    }

    

    /**
     * 根据谓词过滤集合中的元素。
     *
     * @param coll      源集合
     * @param predicate 过滤谓词
     * @param <T>       元素类型
     * @return 符合条件的元素列表
     */
    public static <T> List<T> filter(Collection<T> coll, Predicate<? super T> predicate) {
        if (isEmpty(coll)) {
            return List.of();
        }
        List<T> result = new ArrayList<>();
        for (T item : coll) {
            if (predicate.test(item)) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * 将集合中的元素通过映射函数转换为新列表。
     *
     * @param coll   源集合
     * @param mapper 映射函数
     * @param <T>    源元素类型
     * @param <R>    目标元素类型
     * @return 映射后的列表
     */
    public static <T, R> List<R> map(Collection<T> coll, Function<? super T, ? extends R> mapper) {
        if (isEmpty(coll)) {
            return List.of();
        }
        List<R> result = new ArrayList<>(coll.size());
        for (T item : coll) {
            result.add(mapper.apply(item));
        }
        return result;
    }

    

    /**
     * 计算两个集合的并集。
     *
     * @param a  第一个集合
     * @param b  第二个集合
     * @param <T> 元素类型
     * @return 并集结果 Set
     */
    public static <T> Set<T> union(Collection<T> a, Collection<T> b) {
        Set<T> result = new HashSet<>();
        if (a != null) {
            result.addAll(a);
        }
        if (b != null) {
            result.addAll(b);
        }
        return result;
    }

    /**
     * 计算两个集合的交集。
     *
     * @param a  第一个集合
     * @param b  第二个集合
     * @param <T> 元素类型
     * @return 交集结果 Set
     */
    public static <T> Set<T> intersection(Collection<T> a, Collection<T> b) {
        if (isEmpty(a) || isEmpty(b)) {
            return Set.of();
        }
        Set<T> result = new HashSet<>(a);
        result.retainAll(new HashSet<>(b));
        return result;
    }

    

    /**
     * 拼接多个集合为一个列表。
     *
     * @param first 第一个集合
     * @param rest  后续集合（可变参数）
     * @param <T>   元素类型
     * @return 拼接后的列表
     */
    @SafeVarargs
    public static <T> List<T> concat(Collection<T> first, Collection<T>... rest) {
        List<T> result = new ArrayList<>();
        if (first != null) {
            result.addAll(first);
        }
        if (rest != null) {
            for (Collection<T> coll : rest) {
                if (coll != null) {
                    result.addAll(coll);
                }
            }
        }
        return result;
    }

    

    /**
     * 获取集合的第一个元素；若集合为空则返回 null。
     *
     * @param coll 目标集合
     * @param <T>  元素类型
     * @return 第一个元素，集合为空时返回 null
     */
    public static <T> T first(Collection<T> coll) {
        if (isEmpty(coll)) {
            return null;
        }
        if (coll instanceof List<T> list) {
            return list.get(0);
        }
        return coll.iterator().next();
    }

    /**
     * 创建一个包含单个元素的不可变列表。
     *
     * @param item 元素
     * @param <T>  元素类型
     * @return 只含一个元素的列表
     */
    public static <T> List<T> singletonList(T item) {
        return Collections.singletonList(item);
    }
}
