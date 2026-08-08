package com.flora.runtime.config.source;

import com.flora.codec.PropsUtil;
import com.flora.runtime.config.ConfigException;
import com.flora.runtime.config.impl.MapConfig;
import com.flora.runtime.config.interfaces.Config;
import com.flora.runtime.config.interfaces.ConfigSource;

/**
 * 从字符串加载配置的来源（properties 格式：{@code key=value}，点号键展开为嵌套结构）。
 */
public class StringConfigSource implements ConfigSource {

    private final String content;
    private final String label;

    public StringConfigSource(String content) {
        this(content, defaultLabel(content));
    }

    public StringConfigSource(String content, String label) {
        if (content == null) throw new ConfigException("配置内容不能为 null");
        this.content = content;
        this.label = label == null ? defaultLabel(content) : label;
    }

    @Override
    public Config load() {
        try {
            return MapConfig.of(PropsUtil.parse(content));
        } catch (RuntimeException e) {
            throw new ConfigException("解析配置字符串失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String describe() {
        return "string:" + label;
    }

    private static String defaultLabel(String content) {
        return content.length() > 40 ? content.substring(0, 37) + "..." : content;
    }
}
