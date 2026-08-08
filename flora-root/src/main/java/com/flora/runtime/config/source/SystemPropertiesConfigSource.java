package com.flora.runtime.config.source;

import com.flora.runtime.config.ConfigException;
import com.flora.runtime.config.ConfigSchema;
import com.flora.runtime.config.impl.MapConfig;
import com.flora.runtime.config.interfaces.Config;
import com.flora.runtime.config.interfaces.ConfigSource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 从系统属性（{@code -Dkey=value}）加载配置的来源。
 * <p>由 {@link ConfigSchema} 声明要读取的 key，属性名<b>原样识别</b>，
 * schema 声明的 key 在系统属性中缺失时结果为 {@code null}（「缺失填 null」）。</p>
 */
public class SystemPropertiesConfigSource implements ConfigSource {

    private final ConfigSchema schema;

    public SystemPropertiesConfigSource(ConfigSchema schema) {
        if (schema == null) throw new ConfigException("ConfigSchema 不能为 null");
        this.schema = schema;
    }

    @Override
    public Config load() {
        Map<String, Object> flat = new LinkedHashMap<>();
        for (String key : schema.keys()) {
            flat.put(key, System.getProperty(key));
        }
        return MapConfig.of(expand(flat));
    }

    @Override
    public String describe() {
        return "sysprop:" + schema.keys();
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
