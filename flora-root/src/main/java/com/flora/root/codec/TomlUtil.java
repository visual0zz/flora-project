package com.flora.root.codec;

import java.util.Map;

import com.flora.root.codec.toml.TomlBuilder;
import com.flora.root.codec.toml.TomlParser;

/**
 * TOML 工具门面。
 * <p>纯内存操作，不涉及文件 I/O。所有方法委托至 {@code com.flora.codec.toml} 内部包。</p>
 *
 * <p>线程安全（委托的解析器/序列化器均为 per-call stateless）。</p>
 */
public final class TomlUtil {

    private TomlUtil() {}

    /**
     * 解析 TOML 文本为 {@code Map<String, Object>}。
     * @throws IllegalStateException 解析失败
     * @throws IllegalArgumentException src 为 null
     */
    public static Map<String, Object> parse(String src) {
        return TomlParser.parse(src);
    }

    /**
     * 将 {@code Map<String, Object>} 序列化为 TOML 文本。
     */
    public static String toTomlString(Map<String, Object> map) {
        if (map == null) throw new IllegalArgumentException("map 为 null");
        return TomlBuilder.toTomlString(map);
    }
}
