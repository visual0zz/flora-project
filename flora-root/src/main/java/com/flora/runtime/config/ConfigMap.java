package com.flora.runtime.config;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 不可变的层次化配置映射，支持点号路径访问和类型安全取值。
 * <p>包装来自解析器（{@code Map<String, Object>}）的嵌套结构，
 * 提供便捷的类型转换方法。所有取值方法均为 null-safe。</p>
 */
public final class ConfigMap {

    private static final ConfigMap EMPTY = new ConfigMap(Map.of());

    private final Map<String, Object> raw;

    private ConfigMap(Map<String, Object> raw) {
        this.raw = Collections.unmodifiableMap(copyDeep(raw));
    }

    // ====== 工厂方法 ======

    /** 创建空配置。 */
    public static ConfigMap empty() {
        return EMPTY;
    }

    /** 包装原始 Map。 */
    public static ConfigMap of(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return EMPTY;
        return new ConfigMap(map);
    }

    // ====== 路径访问 ======

    /**
     * 按点号路径获取值（如 {@code "a.b.c"}），路径不存在时返回 null。
     */
    @SuppressWarnings("unchecked")
    public Object get(String path) {
        return resolve(path);
    }

    /**
     * 按点号路径获取字符串值。
     */
    public String getString(String path) {
        Object v = resolve(path);
        if (v == null) return null;
        if (v instanceof String) return (String) v;
        return v.toString();
    }

    /**
     * 按点号路径获取字符串值，不存在时返回默认值。
     */
    public String getString(String path, String defaultValue) {
        Object v = resolve(path);
        if (v == null) return defaultValue;
        if (v instanceof String) return (String) v;
        return v.toString();
    }

    /**
     * 按点号路径获取整型值。
     */
    public Integer getInt(String path) {
        Object v = resolve(path);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) {
            throw new ConfigException("路径 '" + path + "' 的值无法转换为 int: " + v);
        }
    }

    /**
     * 按点号路径获取长整型值。
     */
    public Long getLong(String path) {
        Object v = resolve(path);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(v.toString()); } catch (NumberFormatException e) {
            throw new ConfigException("路径 '" + path + "' 的值无法转换为 long: " + v);
        }
    }

    /**
     * 按点号路径获取布尔值。
     */
    public Boolean getBoolean(String path) {
        Object v = resolve(path);
        if (v == null) return null;
        if (v instanceof Boolean) return (Boolean) v;
        String s = v.toString().toLowerCase();
        if ("true".equals(s)) return Boolean.TRUE;
        if ("false".equals(s)) return Boolean.FALSE;
        throw new ConfigException("路径 '" + path + "' 的值无法转换为 boolean: " + v);
    }

    /**
     * 按点号路径获取子映射。
     */
    @SuppressWarnings("unchecked")
    public ConfigMap getMap(String path) {
        Object v = resolve(path);
        if (v == null) return null;
        if (v instanceof Map) return new ConfigMap((Map<String, Object>) v);
        throw new ConfigException("路径 '" + path + "' 的值不是映射类型: " + v.getClass().getName());
    }

    /**
     * 按点号路径获取列表。
     */
    @SuppressWarnings("unchecked")
    public List<Object> getList(String path) {
        Object v = resolve(path);
        if (v == null) return null;
        if (v instanceof List) return Collections.unmodifiableList((List<Object>) v);
        throw new ConfigException("路径 '" + path + "' 的值不是列表类型: " + v.getClass().getName());
    }

    /**
     * 检查路径是否存在且值不为 null。
     */
    public boolean contains(String path) {
        return resolve(path) != null;
    }

    /** 返回原始底层 Map 的不可变视图。 */
    public Map<String, Object> toMap() {
        return raw;
    }

    /** 检查是否为空。 */
    public boolean isEmpty() {
        return raw.isEmpty();
    }

    @Override
    public String toString() {
        return raw.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConfigMap cm)) return false;
        return raw.equals(cm.raw);
    }

    @Override
    public int hashCode() {
        return raw.hashCode();
    }

    // ====== 内部实现 ======

    @SuppressWarnings("unchecked")
    private Object resolve(String path) {
        if (path == null || path.isEmpty()) return null;
        String[] parts = path.split("\\.");
        Object current = raw;
        for (String key : parts) {
            if (!(current instanceof Map)) return null;
            Map<String, Object> m = (Map<String, Object>) current;
            current = m.get(key);
            if (current == null) return null;
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> copyDeep(Map<String, Object> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : map.entrySet()) {
            Object value = e.getValue();
            if (value instanceof Map) {
                value = copyDeep((Map<String, Object>) value);
            } else if (value instanceof List) {
                value = copyList((List<Object>) value);
            }
            result.put(e.getKey(), value);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> copyList(List<Object> list) {
        List<Object> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map) {
                result.add(copyDeep((Map<String, Object>) item));
            } else if (item instanceof List) {
                result.add(copyList((List<Object>) item));
            } else {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * 合并多个 ConfigMap（后者的值覆盖前者）。
     */
    static ConfigMap merge(ConfigMap base, ConfigMap overlay) {
        if (base.isEmpty()) return overlay;
        if (overlay.isEmpty()) return base;
        Map<String, Object> merged = new LinkedHashMap<>(copyDeep(base.raw));
        mergeDeep(merged, copyDeep(overlay.raw));
        return new ConfigMap(merged);
    }

    @SuppressWarnings("unchecked")
    private static void mergeDeep(Map<String, Object> target, Map<String, Object> source) {
        for (Map.Entry<String, Object> e : source.entrySet()) {
            String key = e.getKey();
            Object sourceVal = e.getValue();
            Object targetVal = target.get(key);
            if (targetVal instanceof Map && sourceVal instanceof Map) {
                mergeDeep((Map<String, Object>) targetVal, (Map<String, Object>) sourceVal);
            } else {
                target.put(key, sourceVal);
            }
        }
    }
}
