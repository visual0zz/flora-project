package com.flora.root.runtime.config.source;

import com.flora.root.runtime.config.ConfigException;
import com.flora.root.runtime.config.ConfigSchema;
import com.flora.root.runtime.config.impl.MapConfig;
import com.flora.root.runtime.config.interfaces.Config;
import com.flora.root.runtime.config.interfaces.ConfigSource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 从环境变量加载配置的来源。
 * <p>由 {@link ConfigSchema} 声明要读取的 key，环境变量名<b>原样识别</b>（不做大小写/分隔符转换），
 * schema 声明的 key 在环境中缺失时结果为 {@code null}（「缺失填 null」）。</p>
 */
public class EnvConfigSource implements ConfigSource {

    private final ConfigSchema schema;

    public EnvConfigSource(ConfigSchema schema) {
        if (schema == null) throw new ConfigException("ConfigSchema 不能为 null");
        this.schema = schema;
    }

    @Override
    public Config load() {
        Map<String, Object> flat = new LinkedHashMap<>();
        for (String key : schema.keys()) {
            flat.put(key, System.getenv(key));
        }
        return MapConfig.of(expand(flat));
    }

    @Override
    public String describe() {
        return "env:" + schema.keys();
    }

    /** 把扁平点号键展开为嵌套 LinkedHashMap（与 RemoteConfigSource 一致），null 值照常写入。 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> expand(Map<String, Object> flat) {
        Map<String, Object> root = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : flat.entrySet()) {
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
}
