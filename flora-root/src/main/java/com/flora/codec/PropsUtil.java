package com.flora.codec;

import com.flora.codec.props.PropsBuilder;
import com.flora.codec.props.PropsParser;

import java.util.Map;

/**
 * Properties 工具门面类，整合解析与序列化功能。
 * <p>所有方法均委托给 {@link PropsParser} 与 {@link PropsBuilder}。</p>
 *
 * <p>解析将「点号键」展开为嵌套 {@link Map}（如 {@code a.b.c=1} → {@code {a:{b:{c:"1"}}}），
 * 与 {@link JsonUtil} / {@link YamlUtil} 共用同一内存模型；叶子值保持 {@code String}。</p>
 */
public final class PropsUtil {

    private PropsUtil() {}

    /**
     * 解析 .properties 文本为嵌套 Map。
     *
     * @param src properties 文本
     * @return 展开点号键后的嵌套 Map
     * @see PropsParser#parse(String)
     */
    public static Map<String, Object> parse(String src) {
        return PropsParser.parse(src);
    }

    /**
     * 解析 .properties 文本（顶层恒为映射，签名与 {@link #parse(String)} 一致，便于统一调用）。
     *
     * @param src properties 文本
     * @return 嵌套 Map
     */
    public static Map<String, Object> parseObject(String src) {
        return PropsParser.parse(src);
    }

    /**
     * 将嵌套 Map 序列化为 .properties 文本（反向扁平化为点号键）。
     *
     * @param map 嵌套 Map
     * @return properties 文本
     * @see PropsBuilder#build(Map)
     */
    public static String toPropertiesString(Map<String, Object> map) {
        if (map == null) throw new IllegalArgumentException("map 为 null");
        return PropsBuilder.build(map);
    }
}
