package com.flora.runtime.config.interfaces;

import java.util.List;

/**
 * 流式配置视图，支持链式类型化取值。
 * <p>相比 {@link Config}，{@link #getSubConfig(String)} 将返回类型协变为
 * {@link FluentConfig}，使 {@code getConfig("db").getConfig("mysql").getInt("port")}
 * 这类深层链式调用成为可能；同时提供 null-safe 的类型化取值方法与带默认值的变体。
 * 实际取值与类型转换行为由底层实现承载。</p>
 */
public interface FluentConfig extends Config {

    /** 按点号路径获取子配置，返回类型协变为 {@link FluentConfig} 以支持链式调用。路径不存在时返回 null。 */
    @Override
    FluentConfig getSubConfig(String path);

    /** 按点号路径获取字符串值，缺失时返回 null；非字符串值取 {@code toString()}。 */
    String getString(String path);

    /** 按点号路径获取整型值，缺失时返回 null；可转换的数值或字符串被转换，否则抛 {@link com.flora.runtime.config.ConfigException}。 */
    Integer getInt(String path);

    /** 按点号路径获取长整型值，缺失时返回 null；可转换的数值或字符串被转换，否则抛 {@link com.flora.runtime.config.ConfigException}。 */
    Long getLong(String path);

    /** 按点号路径获取布尔值，缺失时返回 null；{@code "true"}/{@code "false"} 字符串被转换，否则抛 {@link com.flora.runtime.config.ConfigException}。 */
    Boolean getBoolean(String path);

    /** 按点号路径获取列表，缺失时返回 null；值不是列表类型时抛 {@link com.flora.runtime.config.ConfigException}。 */
    List<Object> getList(String path);

    /** 按点号路径获取字符串值，缺失时返回默认值。 */
    default String getStringOrDefault(String path, String defaultValue) {
        String v = getString(path);
        return v != null ? v : defaultValue;
    }

    /** 按点号路径获取整型值，缺失时返回默认值。 */
    default int getIntOrDefault(String path, int defaultValue) {
        Integer v = getInt(path);
        return v != null ? v : defaultValue;
    }

    /** 按点号路径获取长整型值，缺失时返回默认值。 */
    default long getLongOrDefault(String path, long defaultValue) {
        Long v = getLong(path);
        return v != null ? v : defaultValue;
    }

    /** 按点号路径获取布尔值，缺失时返回默认值。 */
    default boolean getBooleanOrDefault(String path, boolean defaultValue) {
        Boolean v = getBoolean(path);
        return v != null ? v : defaultValue;
    }
}
