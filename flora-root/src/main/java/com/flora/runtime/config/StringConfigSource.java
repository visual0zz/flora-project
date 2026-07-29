package com.flora.runtime.config;

/**
 * 从字符串文本中加载配置的源。
 * <p>主要用于测试或内联配置场景。</p>
 */
public class StringConfigSource implements ConfigSource {

    private final ConfigFormat format;
    private final String content;
    private final String label;

    /**
     * 创建字符串配置源。
     *
     * @param format  配置格式
     * @param content 配置文本
     * @param label   来源标签（用于描述）
     */
    public StringConfigSource(ConfigFormat format, String content, String label) {
        this.format = format;
        this.content = content;
        this.label = label;
    }

    /**
     * 创建字符串配置源，标签自动生成。
     */
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
