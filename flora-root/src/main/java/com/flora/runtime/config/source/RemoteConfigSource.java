package com.flora.runtime.config.source;

import com.flora.common.RemoteKVSource;
import com.flora.runtime.config.ConfigException;
import com.flora.runtime.config.ConfigSchema;
import com.flora.runtime.config.interfaces.Config;
import com.flora.runtime.config.interfaces.ConfigSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从远程键值源加载配置的来源。
 * <p>组合一个 {@link RemoteKVSource} 与一个 {@link ConfigSchema}：schema 声明了配置包含的 key
 * （点号路径形式），每个 key 直接作为远端键读取。加载时逐个读取，远端缺失的 key 在结果中
 * 仍会出现且值为 {@code null}（「缺失一律填 null」）。点号路径展开为嵌套结构，使
 * {@code Config.get("db.host")} 可访问。</p>
 * <p>远端读取失败（kv.get 抛运行时异常）时包装为 {@link ConfigException}。</p>
 */
public class RemoteConfigSource implements ConfigSource {

    private final RemoteKVSource kv;
    private final ConfigSchema schema;

    public RemoteConfigSource(RemoteKVSource kv, ConfigSchema schema) {
        if (kv == null) throw new ConfigException("RemoteKVSource 不能为 null");
        if (schema == null) throw new ConfigException("ConfigSchema 不能为 null");
        this.kv = kv;
        this.schema = schema;
    }

    @Override
    public Config load() {
        Map<String, String> flat = new LinkedHashMap<>();
        for (String key : schema.keys()) {
            try {
                flat.put(key, kv.get(key));
            } catch (RuntimeException e) {
                throw new ConfigException("读取远程配置 key 失败: " + key, e);
            }
        }
        return new MapConfig(expand(flat));
    }

    @Override
    public String describe() {
        return "remote(" + kv.getClass().getName() + ") schema=" + schema.keys();
    }

    /** 把扁平点号键展开为嵌套 LinkedHashMap，null 值照常写入。 */
    private static Map<String, Object> expand(Map<String, String> flat) {
        Map<String, Object> root = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : flat.entrySet()) {
            String[] segments = e.getKey().split("\\.");
            Map<String, Object> current = root;
            for (int i = 0; i < segments.length - 1; i++) {
                Object next = current.get(segments[i]);
                if (!(next instanceof Map)) {
                    Map<String, Object> child = new LinkedHashMap<>();
                    current.put(segments[i], child);
                    next = child;
                }
                current = (Map<String, Object>) next;
            }
            current.put(segments[segments.length - 1], e.getValue());
        }
        return root;
    }

    /** 加载结果的不可变 Config 包装，支持点号路径访问。 */
    private static final class MapConfig implements Config {

        private final Map<String, Object> raw;

        MapConfig(Map<String, Object> raw) {
            this.raw = Collections.unmodifiableMap(deepCopy(raw));
        }

        @Override
        public Object get(String path) {
            return resolve(path);
        }

        @Override
        public Config getSubConfig(String path) {
            Object v = resolve(path);
            if (v == null) return null;
            if (v instanceof Map) {
                return new MapConfig((Map<String, Object>) v);
            }
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
    }
}
