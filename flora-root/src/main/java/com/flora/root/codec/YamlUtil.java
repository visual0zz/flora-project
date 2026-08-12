package com.flora.root.codec;

import com.flora.root.codec.yaml.YamlBuilder;
import com.flora.root.codec.yaml.YamlParser;

import java.util.List;
import java.util.Map;

/**
 * YAML 工具门面类，整合解析与序列化功能。
 * <p>所有方法均委托给 {@link YamlParser} 与 {@link YamlBuilder}。</p>
 *
 * <p>解析结果使用与 {@link JsonUtil} / {@link PropsUtil} 相同的内存模型
 * （{@code Map<String,Object>} / {@code List<Object>} / 标量），便于 {@code com.flora.runtime.config} 统一消费。</p>
 */
public final class YamlUtil {

    private YamlUtil() {}

    /**
     * 解析 YAML 文本，返回首个/唯一文档根节点。
     *
     * @param src YAML 文本
     * @return 解析后的节点（Map / List / 标量），文本为空时返回 null
     * @see YamlParser#parse(String)
     */
    public static Object parse(String src) {
        return YamlParser.parse(src);
    }

    /**
     * 解析 YAML 文本并确保顶层为映射。
     *
     * @param src YAML 文本
     * @return 解析后的 Map
     */
    public static Map<String, Object> parseObject(String src) {
        Object v = YamlParser.parse(src);
        if (!(v instanceof Map)) throw new IllegalStateException("YAML 顶层不是映射");
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) v;
        return m;
    }

    /**
     * 解析 YAML 文本并确保顶层为序列。
     *
     * @param src YAML 文本
     * @return 解析后的 List
     */
    public static List<Object> parseArray(String src) {
        Object v = YamlParser.parse(src);
        if (!(v instanceof List)) throw new IllegalStateException("YAML 顶层不是序列");
        @SuppressWarnings("unchecked")
        List<Object> l = (List<Object>) v;
        return l;
    }

    /**
     * 解析 YAML 文本中的所有文档，返回每篇文档根组成的列表。
     *
     * @param src YAML 文本
     * @return 文档根列表（无文档时为空列表）
     */
    public static List<Object> parseDocuments(String src) {
        return YamlParser.parseDocuments(src);
    }

    /**
     * 将对象序列化为 YAML 文本（紧凑，使用块样式）。
     *
     * @param obj 待序列化对象（Map / List / 标量）
     * @return YAML 文本
     * @see YamlBuilder#toYamlString(Object)
     */
    public static String toYamlString(Object obj) {
        if (obj == null) throw new IllegalArgumentException("obj 为 null");
        return YamlBuilder.toYamlString(obj);
    }
}
