package com.flora.runtime.config.impl;

import com.flora.runtime.config.ConfigException;
import com.flora.runtime.config.interfaces.Config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于嵌套 {@code Map} 的不可变 {@link Config} 实现。
 * <p>构造时深拷贝并包装为不可变视图，支持点号路径访问。
 * 作为 {@code com.flora.runtime.config} 加载合并结果的基础表示。</p>
 */
public final class MapConfig implements Config {

    private static final Config EMPTY = new MapConfig(Map.of());

    private final Map<String, Object> raw;

    public MapConfig(Map<String, Object> raw) {
        this.raw = Collections.unmodifiableMap(deepCopy(raw));
    }

    /** 跳过深拷贝的私有构造：调用方保证 {@code raw} 已递归只读（由 {@link #readOnlyView} 使用）。 */
    private MapConfig(Map<String, Object> raw, boolean alreadyReadOnly) {
        this.raw = raw;
    }

    /** 创建空配置。 */
    public static Config empty() {
        return EMPTY;
    }

    /** 包装嵌套 Map（深拷贝为不可变）。 */
    public static Config of(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) return EMPTY;
        return new MapConfig(raw);
    }

    /**
     * 创建嵌套结构的<b>只读视图</b>：递归把每层 Map/List 包装为不可变容器，
     * 不深拷贝数据（标量引用原样复用），直接持有传入结构引用。
     * <p>用于 {@code view()} 等需要"轻量只读快照、拒绝写"的场景；
     * 包装后任一层级的写操作（外层或嵌套的 {@code put}/{@code add}）都会抛
     * {@link UnsupportedOperationException}。</p>
     */
    public static Config readOnlyView(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) return EMPTY;
        return new MapConfig(deepReadOnly(raw), true);
    }

    @Override
    public Object get(String path) {
        Object v = resolve(path);
        if (v instanceof Map) {
            // 子结构 → 返回可继续下钻的子视图（与 ConfigView.get 语义一致，共享本树作解释上下文）
            return new LazyConfigView(() -> this.raw, path);
        }
        return v;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Config getSubConfig(String path) {
        Object v = resolve(path);
        if (v == null) return null;
        if (v instanceof Map) return new MapConfig((Map<String, Object>) v);
        throw new ConfigException("路径 '" + path + "' 的值不是映射类型: " + v.getClass().getName());
    }

    @Override
    public Map<String, Object> toMapTree() {
        return raw;
    }

    @Override
    public Map<String, Object> toLongKeyMap() {
        Map<String, Object> flat = new LinkedHashMap<>();
        flatten("", raw, flat);
        return Collections.unmodifiableMap(flat);
    }

    @Override
    public boolean isEmpty() {
        return raw.isEmpty();
    }

    @SuppressWarnings("unchecked")
    private Object resolve(String path) {
        if (path == null || path.isEmpty()) return null;
        Object current = raw;
        for (String key : path.split("\\.")) {
            if (!(current instanceof Map)) return null;
            current = ((Map<String, Object>) current).get(key);
            if (current == null) return null;
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepCopy(Map<String, Object> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : map.entrySet()) {
            Object value = e.getValue();
            if (value instanceof Map) {
                value = deepCopy((Map<String, Object>) value);
            } else if (value instanceof List) {
                value = deepCopyList((List<Object>) value);
            }
            result.put(e.getKey(), value);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> deepCopyList(List<Object> list) {
        List<Object> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map) {
                result.add(deepCopy((Map<String, Object>) item));
            } else if (item instanceof List) {
                result.add(deepCopyList((List<Object>) item));
            } else {
                result.add(item);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** 递归只读包装（不拷贝数据，仅容器层建不可变视图）。 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> deepReadOnly(Map<String, Object> map) {
        Map<String, Object> result = new LinkedHashMap<>(map.size());
        for (Map.Entry<String, Object> e : map.entrySet()) {
            result.put(e.getKey(), deepReadOnlyValue(e.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    @SuppressWarnings("unchecked")
    private static Object deepReadOnlyValue(Object value) {
        if (value instanceof Map) {
            return deepReadOnly((Map<String, Object>) value);
        }
        if (value instanceof List) {
            List<Object> result = new ArrayList<>(((List<Object>) value).size());
            for (Object item : (List<Object>) value) {
                result.add(deepReadOnlyValue(item));
            }
            return Collections.unmodifiableList(result);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static void flatten(String prefix, Map<String, Object> map, Map<String, Object> out) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String key = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
            Object v = e.getValue();
            if (v instanceof Map) {
                flatten(key, (Map<String, Object>) v, out);
            } else {
                out.put(key, v);
            }
        }
    }
}
