package com.flora.runtime.config.impl;

import com.flora.runtime.config.Config;
import com.flora.runtime.config.ConfigFormat;
import com.flora.runtime.config.ConfigSource;

/**
 * 从字符串文本中加载配置的源。主要用于测试或内联配置场景。
 */
public class StringConfigSource implements ConfigSource {

    private final ConfigFormat format;
    private final String content;
    private final String label;

    public StringConfigSource(ConfigFormat format, String content, String label) {
        this.format = format;
        this.content = content;
        this.label = label;
    }

    public StringConfigSource(ConfigFormat format, String content) {
        this(format, content, "<inline>");
    }

    @Override
    public Config load() {
        return Config.of(format.parse(content));
    }

    @Override
    public String describe() {
        return "string:" + label + " (" + format.name() + ")";
    }

    @Override
    public String location() {
        return label;
    }
}
