package com.flora.runtime.config.source;

import com.flora.codec.JsonUtil;
import com.flora.codec.PropsUtil;
import com.flora.codec.TomlUtil;
import com.flora.codec.YamlUtil;
import com.flora.runtime.config.ConfigException;
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

    @Override
    public String location() {
        return filePath.toString();
    }

    private String readFile() {
        if (!Files.isRegularFile(filePath)) {
            throw new ConfigException("配置文件不存在或不是普通文件: " + filePath);
        }
        try {
            return new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ConfigException("读取配置文件失败: " + filePath, e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String text) {
        String name = filePath.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            throw new ConfigException("无法从文件名识别配置格式: " + name);
        }
        String ext = name.substring(dot + 1).toLowerCase();
        try {
            return switch (ext) {
                case "json" -> JsonUtil.parseObject(text);
                case "yaml", "yml" -> YamlUtil.parseObject(text);
                case "toml" -> TomlUtil.parse(text);
                case "properties", "props" -> PropsUtil.parse(text);
                default -> throw new ConfigException("不支持的配置格式: ." + ext);
            };
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
        public Object get(String path) {
            return resolve(path);
        }

        @Override
        public Config getConfig(String path) {
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
    }
}
