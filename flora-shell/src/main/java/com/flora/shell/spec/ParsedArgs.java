package com.flora.shell.spec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 参数解析结果。按参数名（选项长名 / 位置参数名）存储解析后的值。
 * <p>由 {@link com.flora.shell.spec.ArgParser} 解析产生，命令在 {@code execute} 中通过
 * {@code get}/{@code getInt}/{@code getStringList} 等读取。</p>
 */
public final class ParsedArgs {

    private final Map<String, Object> values = new LinkedHashMap<>();

    ParsedArgs() {
    }

    void put(String name, Object value) {
        values.put(name, value);
    }

    /**
     * @param name 参数名
     * @return 该参数是否已提供（含默认值填充后）
     */
    public boolean contains(String name) {
        return values.containsKey(name);
    }

    /**
     * @param name 参数名
     * @return 该参数的值；不存在返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String name) {
        return (T) values.get(name);
    }

    /**
     * @param name 参数名
     * @return 解析为 int 的值；不存在或非法时抛 {@link IllegalArgumentException}
     */
    public int getInt(String name) {
        Object v = values.get(name);
        if (v == null) {
            throw new IllegalArgumentException("缺少参数: " + name);
        }
        return (int) v;
    }

    /**
     * @param name 参数名
     * @return 布尔开关值；不存在返回 {@code false}
     */
    public boolean getBoolean(String name) {
        Object v = values.get(name);
        return v instanceof Boolean b && b;
    }

    /**
     * @param name 参数名
     * @return 字符串列表（变长位置参数 / 可重复选项）；不存在返回空列表
     */
    @SuppressWarnings("unchecked")
    public List<String> getStringList(String name) {
        Object v = values.get(name);
        if (v == null) {
            return List.of();
        }
        if (v instanceof List<?> list) {
            return (List<String>) list;
        }
        return List.of(String.valueOf(v));
    }

    /**
     * @return 全部参数名 → 值的快照
     */
    public Map<String, Object> asMap() {
        return new LinkedHashMap<>(values);
    }

    /**
     * @param name 参数名
     * @param <T>  元素类型
     * @return 可变列表供值累积；不存在则新建并放回
     */
    @SuppressWarnings("unchecked")
    <T> List<T> mutableList(String name) {
        Object v = values.get(name);
        if (v instanceof List<?> list) {
            return (List<T>) list;
        }
        List<T> list = new ArrayList<>();
        values.put(name, list);
        return list;
    }
}
