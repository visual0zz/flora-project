package com.flora.runtime.config.impl;

import com.flora.runtime.config.ConfigException;
import com.flora.runtime.config.interfaces.Config;
import com.flora.runtime.config.interfaces.ConfigView;
import com.flora.runtime.config.interfaces.FluentConfig;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * {@link FluentConfig} 的包装器实现。
 * <p>持有内部 {@link Config}，所有配置访问（{@code get/getSubConfig/toMapTree/toLongKeyMap/isEmpty}）
 * 直接转发给内部对象——{@code Config} 层不处理类型问题，底层解析类型原样透传；
 * 类型化 getter（{@code getString/getInt/getLong/getBoolean/getList}）基于透传值做转换，
 * 转换失败抛 {@link ConfigException}。子配置通过 {@link #of(Config)} 递归包装以维持链式调用。</p>
 */
public class FluentConfigWrapper implements FluentConfig {

    private final Config inner;

    private FluentConfigWrapper(Config inner) {
        this.inner = inner;
    }

    /**
     * 包装普通 {@link Config} 为流式类型化视图；已是 {@link FluentConfig} 时原样返回。
     *
     * @throws ConfigException config 为 null 时抛出
     */
    public static FluentConfig of(Config config) {
        if (config == null) throw new ConfigException("Config 不能为 null");
        return config instanceof FluentConfig fluent ? fluent : new FluentConfigWrapper(config);
    }

    // ====== 转发 Config 访问 ======

    @Override
    public Object get(String path) {
        return inner.get(path);
    }

    @Override
    public FluentConfig getSubConfig(String path) {
        Config sub = inner.getSubConfig(path);
        return sub == null ? null : of(sub);
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
    @Override
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
    @Override
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
    @Override
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
    @Override
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
    @Override
    public List<Object> getList(String path) {
        Object v = inner.get(path);
        if (v == null) return null;
        if (v instanceof List) return Collections.unmodifiableList((List<Object>) v);
        throw new ConfigException("路径 '" + path + "' 的值不是列表类型: " + v.getClass().getName());
    }
}
