package com.flora.java;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Map 处理工具类，提供安全取值、缺省填充、键值反转、按值过滤及集合转 Map 等常用操作。
 * <p>所有方法均为 null 安全：map 为 null 时按“空映射”语义处理，并对必要参数做前置校验。</p>
 */
public final class MapUtil {

    private MapUtil() {
    }

    // ==================== 取值 ====================

    /**
     * 从 Map 中安全取值，键缺失或 map 为 null 时返回默认值。
     *
     * @param map          源映射，可为 null
     * @param key          键
     * @param defaultValue 默认值
     * @param <K>          键类型
     * @param <V>          值类型
     * @return 对应值或默认值
     */
    public static <K, V> V getOrDefault(Map<K, V> map, K key, V defaultValue) {
        if (map == null) {
            return defaultValue;
        }
        return map.getOrDefault(key, defaultValue);
    }

    /**
     * 从 Map 中取值，若键缺失则通过延迟求值提供（不写回原映射）。
     *
     * @param map      源映射，可为 null
     * @param key      键
     * @param supplier 缺省值提供器，在键缺失时调用
     * @param <K>      键类型
     * @param <V>      值类型
     * @return 现有值或延迟求值的结果
     */
    public static <K, V> V getOrSupply(Map<K, V> map, K key, Supplier<? extends V> supplier) {
        CheckUtil.notNull(supplier, "缺省值提供器不能为空");
        if (map != null && map.containsKey(key)) {
            return map.get(key);
        }
        return supplier.get();
    }

    // ==================== 缺省填充 ====================

    /**
     * 若键缺失则放入给定值，返回映射中该键对应的值（可能为既有的旧值）。
     *
     * @param map   目标映射，不能为 null
     * @param key   键
     * @param value 待填充的值
     * @param <K>   键类型
     * @param <V>   值类型
     * @return 键对应的最终值
     */
    public static <K, V> V putIfAbsent(Map<K, V> map, K key, V value) {
        CheckUtil.notNull(map, "映射不能为空");
        V prev = map.get(key);
        if (prev == null && !map.containsKey(key)) {
            map.put(key, value);
            return value;
        }
        return prev;
    }

    // ==================== 反转 / 过滤 ====================

    /**
     * 键值反转，返回以原值为键、原键为值的新映射。
     * <p>若原映射中存在重复值，则因反转会产生键冲突而抛出 {@link IllegalArgumentException}。</p>
     *
     * @param map 源映射，可为 null（返回空映射）
     * @param <K> 原键类型
     * @param <V> 原值类型
     * @return 反转后的新映射
     * @throws IllegalArgumentException 若原映射中存在重复值
     */
    public static <K, V> Map<V, K> invert(Map<K, V> map) {
        Map<V, K> result = new HashMap<>();
        if (map != null) {
            for (Map.Entry<K, V> entry : map.entrySet()) {
                V value = entry.getValue();
                if (result.containsKey(value)) {
                    throw new IllegalArgumentException("原映射中存在重复值，无法安全反转: " + value);
                }
                result.put(value, entry.getKey());
            }
        }
        return result;
    }

    /**
     * 按值过滤，返回仅包含值满足谓词的原映射副本（不影响原映射）。
     *
     * @param map       源映射，可为 null（返回空映射）
     * @param predicate 值谓词，不能为 null
     * @param <K>       键类型
     * @param <V>       值类型
     * @return 过滤后的新映射
     */
    public static <K, V> Map<K, V> filterValues(Map<K, V> map, Predicate<? super V> predicate) {
        CheckUtil.notNull(predicate, "值谓词不能为空");
        Map<K, V> result = new HashMap<>();
        if (map != null) {
            for (Map.Entry<K, V> entry : map.entrySet()) {
                if (predicate.test(entry.getValue())) {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return result;
    }

    // ==================== 集合转 Map ====================

    /**
     * 将集合元素按给定的键/值映射函数转换为 Map。
     *
     * @param items      元素集合，可为 null（返回空映射）
     * @param keyMapper  键映射函数，不能为 null
     * @param valueMapper 值映射函数，不能为 null
     * @param <T>        元素类型
     * @param <K>        键类型
     * @param <V>        值类型
     * @return 转换后的新映射
     */
    public static <T, K, V> Map<K, V> toMap(Iterable<? extends T> items,
                                            Function<? super T, ? extends K> keyMapper,
                                            Function<? super T, ? extends V> valueMapper) {
        CheckUtil.notNull(keyMapper, "键映射函数不能为空");
        CheckUtil.notNull(valueMapper, "值映射函数不能为空");
        Map<K, V> result = new HashMap<>();
        if (items != null) {
            for (T item : items) {
                result.put(keyMapper.apply(item), valueMapper.apply(item));
            }
        }
        return result;
    }
}
