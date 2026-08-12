package com.flora.root.runtime.config.impl;

import com.flora.root.runtime.config.ConfigException;
import com.flora.root.runtime.config.interfaces.Config;
import com.flora.root.runtime.config.interfaces.ConfigView;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 类型化配置视图的包装器实现。
 * <p>持有内部 {@link Config}，配置访问（{@code get/toMapTree/toLongKeyMap/isEmpty}）直接转发给内部对象
 * ——{@code Config} 层不处理类型问题，底层解析类型原样透传；
 * 类型化 getter（{@code getString/getInt/getLong/getBoolean/getList}）基于透传值做转换，
 * 转换失败抛 {@link ConfigException}。{@link #getSubConfig(String)} 基于 {@link #get} 下钻，
 * 子配置通过 {@link #of(Config)} 递归包装以维持链式调用。</p>
 */
public final class FluentConfigWrapper implements Config {

    private final Config inner;

    private FluentConfigWrapper(Config inner) {
        this.inner = inner;
    }

    /**
     * 包装普通 {@link Config} 为类型化视图；已是 {@link FluentConfigWrapper} 时原样返回。
     *
     * @throws ConfigException config 为 null 时抛出
     */
    public static FluentConfigWrapper of(Config config) {
        if (config == null) throw new ConfigException("Config 不能为 null");
        return config instanceof FluentConfigWrapper wrapper ? wrapper : new FluentConfigWrapper(config);
    }

    // ====== 转发 Config 访问 ======

    @Override
    public Object get(String path) {
        return inner.get(path);
    }

    /** 按点号路径取子配置（基于 {@link #get} 下钻），返回类型化子视图；路径缺失返回 null，值为标量时抛 {@link ConfigException}。 */
    public FluentConfigWrapper getSubConfig(String path) {
        Object v = get(path);
        if (v == null) return null;
        if (v instanceof Config c) return of(c);
        throw new ConfigException("路径 '" + path + "' 的值不是映射类型: " + v.getClass().getSimpleName());
    }

    @Override
    public Map<String, Object> toMapTree() {
        return inner.toMapTree();
    }

    @Override
    public Map<String, Object> toLongKeyMap() {
        return inner.toLongKeyMap();
    }

    @Override
    public boolean isEmpty() {
        return inner.isEmpty();
    }

    // ====== 类型化取值 ======

    /** 按点号路径获取字符串值，缺失时返回 null；标量（Number/Boolean 等）取 {@code toString()}；子结构（Map/List/子配置）抛 {@link ConfigException}。 */
    public String getString(String path) {
        Object v = inner.get(path);
        if (v == null) return null;
        if (v instanceof String s) return s;
        requireScalar(path, v);
        return v.toString();
    }

    /** 值是否为非标量子结构（子配置/集合），类型化 getter 应报错而非尝试转换。 */
    private static void requireScalar(String path, Object v) {
        if (v instanceof Map || v instanceof List || v instanceof ConfigView) {
            throw new ConfigException("路径 '" + path + "' 的值不是标量: " + v.getClass().getSimpleName());
        }
    }

    /** 按点号路径获取整型值，缺失时返回 null；数值或可解析字符串被转换，否则抛 {@link ConfigException}。 */
    public Integer getInt(String path) {
        Object v = inner.get(path);
        if (v == null) return null;
        requireScalar(path, v);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                throw new ConfigException("路径 '" + path + "' 的值无法转换为 int: " + v);
            }
        }
        throw new ConfigException("路径 '" + path + "' 的值无法转换为 int: " + v);
    }

    /** 按点号路径获取长整型值，缺失时返回 null；数值或可解析字符串被转换，否则抛 {@link ConfigException}。 */
    public Long getLong(String path) {
        Object v = inner.get(path);
        if (v == null) return null;
        requireScalar(path, v);
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException e) {
                throw new ConfigException("路径 '" + path + "' 的值无法转换为 long: " + v);
            }
        }
        throw new ConfigException("路径 '" + path + "' 的值无法转换为 long: " + v);
    }

    /** 按点号路径获取布尔值，缺失时返回 null；{@code Boolean} 或 {@code "true"}/{@code "false"} 字符串被接受，否则抛 {@link ConfigException}。 */
    public Boolean getBoolean(String path) {
        Object v = inner.get(path);
        if (v == null) return null;
        requireScalar(path, v);
        if (v instanceof Boolean b) return b;
        String s = v.toString().toLowerCase();
        if ("true".equals(s)) return Boolean.TRUE;
        if ("false".equals(s)) return Boolean.FALSE;
        throw new ConfigException("路径 '" + path + "' 的值无法转换为 boolean: " + v);
    }

    /** 按点号路径获取列表，缺失时返回 null；值不是列表类型时抛 {@link ConfigException}。 */
    @SuppressWarnings("unchecked")
    public List<Object> getList(String path) {
        Object v = inner.get(path);
        if (v == null) return null;
        if (v instanceof List) return Collections.unmodifiableList((List<Object>) v);
        throw new ConfigException("路径 '" + path + "' 的值不是列表类型: " + v.getClass().getName());
    }

    // ====== 带默认值的变体 ======

    /** 按点号路径获取字符串值，缺失时返回默认值。 */
    public String getStringOrDefault(String path, String defaultValue) {
        String v = getString(path);
        return v != null ? v : defaultValue;
    }

    /** 按点号路径获取整型值，缺失时返回默认值。 */
    public int getIntOrDefault(String path, int defaultValue) {
        Integer v = getInt(path);
        return v != null ? v : defaultValue;
    }

    /** 按点号路径获取长整型值，缺失时返回默认值。 */
    public long getLongOrDefault(String path, long defaultValue) {
        Long v = getLong(path);
        return v != null ? v : defaultValue;
    }

    /** 按点号路径获取布尔值，缺失时返回默认值。 */
    public boolean getBooleanOrDefault(String path, boolean defaultValue) {
        Boolean v = getBoolean(path);
        return v != null ? v : defaultValue;
    }
}
