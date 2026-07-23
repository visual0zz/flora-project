package com.flora.codec;

import com.flora.codec.json.JsonBuilder;
import com.flora.codec.json.JsonParser;
import com.flora.codec.json.JsonPath;

import java.util.List;
import java.util.Map;

/**
 * JSON 工具门面类，整合解析、序列化和路径查询功能。
 * <p>所有方法均委托给 {@link JsonParser}、{@link JsonBuilder} 和 {@link JsonPath}。</p>
 */
public final class JsonUtil {

    private JsonUtil() {}

    // ====== 解析 ======

    /**
     * 解析 JSON 字符串为 Java 对象。
     *
     * @param src JSON 字符串
     * @return 解析后的 Java 对象
     * @see JsonParser#parse(String)
     */
    public static Object parse(String src) {
        return JsonParser.parse(src);
    }

    /**
     * 解析 JSON 字符串并确保顶层为 JSON Object。
     *
     * @param src JSON 字符串
     * @return 解析后的 Map
     * @see JsonParser#parseObject(String)
     */
    public static Map<String, Object> parseObject(String src) {
        return JsonParser.parseObject(src);
    }

    /**
     * 解析 JSON 字符串并确保顶层为 JSON Array。
     *
     * @param src JSON 字符串
     * @return 解析后的 List
     * @see JsonParser#parseArray(String)
     */
    public static List<Object> parseArray(String src) {
        return JsonParser.parseArray(src);
    }

    // ====== 序列化 ======

    /**
     * 将 Java 对象序列化为 JSON 字符串（紧凑格式）。
     *
     * @param obj 要序列化的对象
     * @return JSON 字符串
     * @see JsonBuilder#toJsonString(Object)
     */
    public static String toJsonString(Object obj) {
        return JsonBuilder.toJsonString(obj);
    }

    /**
     * 将 Java 对象序列化为格式化的 JSON 字符串（带缩进）。
     *
     * @param obj 要序列化的对象
     * @return 格式化的 JSON 字符串
     * @see JsonBuilder#toPrettyJsonString(Object)
     */
    public static String toPrettyJsonString(Object obj) {
        return JsonBuilder.toPrettyJsonString(obj);
    }

    // ====== 路径查询 ======

    /**
     * 在解析后的 JSON 对象上执行 JSONPath 表达式查询。
     *
     * @param root 根对象（通常为 Map 或 List）
     * @param path JSONPath 表达式，如 {@code "$.key1.key2[0]"}
     * @return 查询结果，路径不存在时返回 null
     * @see JsonPath#eval(Object, String)
     */
    public static Object eval(Object root, String path) {
        return JsonPath.eval(root, path);
    }
}
