package com.flora.runtime.config.impl;

import com.flora.codec.JsonUtil;
import com.flora.codec.PropsUtil;
import com.flora.codec.TomlUtil;
import com.flora.codec.YamlUtil;
import com.flora.runtime.config.ConfigException;

import java.util.Map;
import java.util.function.Function;

/**
 * 配置格式枚举，关联 {@code com.flora.codec} 包中的解析器。
 */
public enum ConfigSourceFileFormat {

    JSON(JsonUtil::parseObject, "json"),
    YAML(YamlUtil::parseObject, "yaml", "yml"),
    TOML(TomlUtil::parse, "toml"),
    PROPERTIES(PropsUtil::parse, "properties", "props");

    private final Function<String, Map<String, Object>> parser;
    private final String[] extensions;

    ConfigSourceFileFormat(Function<String, Map<String, Object>> parser, String... extensions) {
        this.parser = parser;
        this.extensions = extensions;
    }

    /**
     * 根据文件扩展名推断配置格式。
     *
     * @param filename 文件名
     * @return 匹配的格式
     * @throws ConfigException 无法识别时抛出
     */
    public static ConfigSourceFileFormat fromFilename(String filename) {
        if (filename == null) throw new ConfigException("文件名为 null，无法推断格式");
        int dot = filename.lastIndexOf('.');
        if (dot < 0) throw new ConfigException("文件名缺少扩展名: " + filename);
        String ext = filename.substring(dot + 1).toLowerCase();
        for (ConfigSourceFileFormat fmt : values()) {
            for (String e : fmt.extensions) {
                if (e.equals(ext)) return fmt;
            }
        }
        throw new ConfigException("不支持的文件格式: ." + ext);
    }

    /**
     * 解析文本为配置映射。
     *
     * @param text 待解析文本
     * @return 配置映射
     */
    public Map<String, Object> parse(String text) {
        return parser.apply(text);
    }
}
