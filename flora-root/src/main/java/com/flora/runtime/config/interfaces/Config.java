package com.flora.runtime.config.interfaces;

import com.flora.runtime.config.impl.ConfigImpl;

import java.util.List;
import java.util.Map;

/**
 * 不可变的层次化配置映射，支持点号路径访问和类型安全取值。
 * <p>包装来自解析器（{@code Map<String, Object>}）的嵌套结构，
 * 提供便捷的类型转换方法。所有取值方法均为 null-safe。
 * 实现由 {@link ConfigImpl} 承载，通过 {@link #of(Map)} / {@link #empty()} 创建。</p>
 */
public interface Config {

    // ====== 工厂方法 ======

    /** 创建空配置。 */
    static Config empty() {
        return ConfigImpl.empty();
    }

    /** 包装原始 Map。 */
    static Config of(Map<String, Object> map) {
        return ConfigImpl.of(map);
    }

    /**
     * 合并多个 Config（后者的值覆盖前者）。
     */
    static Config merge(Config base, Config overlay) {
        return ConfigImpl.merge(base, overlay);
    }

    // ====== 路径访问 ======

    /** 按点号路径获取值（如 {@code "a.b.c"}），路径不存在时返回 null。 */
    Object get(String path);

    /** 按点号路径获取字符串值。 */
    String getString(String path);

    /** 按点号路径获取整型值。 */
    Integer getInt(String path);

    /** 按点号路径获取长整型值。 */
    Long getLong(String path);

    /** 按点号路径获取布尔值。 */
    Boolean getBoolean(String path);

    /** 按点号路径获取子映射。 */
    Config getConfig(String path);

    /** 按点号路径获取列表。 */
    List<Object> getList(String path);

    /** 按点号路径获取字符串值，缺失时返回默认值。 */
    default String getOrDefault(String path, String defaultValue) {
        String v = getString(path);
        return v != null ? v : defaultValue;
    }

    /** 按点号路径获取整型值，缺失时返回默认值。 */
    default Integer getOrDefault(String path, Integer defaultValue) {
        Integer v = getInt(path);
        return v != null ? v : defaultValue;
    }

    /** 按点号路径获取长整型值，缺失时返回默认值。 */
    default Long getOrDefault(String path, Long defaultValue) {
        Long v = getLong(path);
        return v != null ? v : defaultValue;
    }

    /** 按点号路径获取布尔值，缺失时返回默认值。 */
    default Boolean getOrDefault(String path, Boolean defaultValue) {
        Boolean v = getBoolean(path);
        return v != null ? v : defaultValue;
    }

    /** 检查路径是否存在且值不为 null。 */
    boolean contains(String path);

    /** 返回原始底层 Map 的不可变视图。 */
    Map<String, Object> toMap();

    /** 检查是否为空。 */
    boolean isEmpty();
}
