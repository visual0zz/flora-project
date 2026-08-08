package com.flora.runtime.config.source;

import com.flora.runtime.config.ConfigException;
import com.flora.runtime.config.impl.ConfigSourceFileFormat;
import com.flora.runtime.config.interfaces.Config;
import com.flora.runtime.config.interfaces.ConfigSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从文件系统加载配置的来源。
 * <p>根据文件扩展名自动识别格式并委托对应解析器：{@code .json}、{@code .yaml}/{@code .yml}、
 * {@code .toml}、{@code .properties}/{@code .props}，解析结果顶层必须是映射。
 * 文件缺失、不可读、格式不支持或解析失败时抛 {@link ConfigException}。</p>
 */
public class FileConfigSource implements ConfigSource {

    private final Path filePath;

    public FileConfigSource(Path filePath) {
        this.filePath = filePath.toAbsolutePath().normalize();
    }

    @Override
    public Config load() {
        return new MapConfig(parse(readFile()));
    }

    @Override
    public String describe() {
        return "file:" + filePath;
    }

    private String readFile() {
        if (!Files.isRegularFile(filePath)) {
            throw new ConfigException("配置文件不存在或不是普通文件: " + filePath);
        }
        try {
            return Files.readString(filePath);
        } catch (IOException e) {
            throw new ConfigException("读取配置文件失败: " + filePath, e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String text) {
        String name = filePath.getFileName().toString();
        try {
            return ConfigSourceFileFormat.fromFilename(name).parse(text);
        } catch (ConfigException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ConfigException("解析配置文件失败: " + filePath + " —— " + e.getMessage(), e);
        }
    }

    /** 解析结果的不可变 Config 包装，支持点号路径访问。 */
    private static final class MapConfig implements Config {

        private final Map<String, Object> raw;

        MapConfig(Map<String, Object> raw) {
            this.raw = Collections.unmodifiableMap(deepCopy(raw));
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object get(String path) {
            Object v = resolve(path);
            if (v instanceof Map) {
                // 子结构 → 返回可继续下钻的子 Config
                return new MapConfig((Map<String, Object>) v);
            }
            return v;
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
