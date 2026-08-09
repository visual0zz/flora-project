package com.flora.codec;

import com.flora.codec.json.JsonArray;
import com.flora.codec.json.JsonBuilder;
import com.flora.codec.json.JsonObject;
import com.flora.codec.json.JsonParser;
import com.flora.codec.json.JsonPath;
import com.flora.codec.json.JsonValue;
import com.flora.tag.ModuleEntry;

import java.util.List;

/**
 * JSON 工具门面类，整合解析、序列化和路径查询功能。
 * <p>所有方法均委托给 {@link JsonParser}、{@link JsonBuilder} 和 {@link JsonPath}。
 * 解析默认产出的核心数据类型为 {@link JsonValue}（{@link JsonObject} / {@link JsonArray} 等），
 * 也可经 {@link JsonObject#toMap()} / {@link JsonArray#toNative()} 转换为原生 {@code Map}/{@code List} 树。</p>
 */
@ModuleEntry
public final class JsonUtil {

    private JsonUtil() {}

    // ====== 解析 ======

    /**
     * 解析 JSON 字符串为 {@link JsonValue} 模型。
     *
     * @param src JSON 字符串
     * @return 解析后的 JsonValue
     * @see JsonParser#parse(String)
     */
    public static JsonValue parse(String src) {
        return JsonParser.parse(src);
    }

    /**
     * 解析 JSON 字符串并确保顶层为 JSON Object。
     *
     * @param src JSON 字符串
     * @return 解析后的 JsonObject
     * @see JsonParser#parseObject(String)
     */
    public static JsonObject parseObject(String src) {
        return JsonParser.parseObject(src);
    }

    /**
     * 解析 JSON 字符串并确保顶层为 JSON Array。
     *
     * @param src JSON 字符串
     * @return 解析后的 JsonArray
     * @see JsonParser#parseArray(String)
     */
    public static JsonArray parseArray(String src) {
        return JsonParser.parseArray(src);
    }

    // ====== 序列化 ======

    /**
     * 将 Java 对象序列化为 JSON 字符串（紧凑格式）。
     * <p>支持 {@link JsonValue} 模型、{@code Map}/{@code List}、数组、字符串、数字、布尔值、枚举与普通 Java Bean。
     * 普通 Bean 经 {@link JsonObject#fromBean} 收集 getter 属性（{@code JsonIgnore} 可排除）。</p>
     *
     * @param obj 要序列化的对象
     * @return JSON 字符串
     * @see JsonBuilder#toJsonString(Object)
     */
    public static String toJsonString(Object obj) {
        if (obj == null) throw new IllegalArgumentException("obj 为 null");
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
        if (obj == null) throw new IllegalArgumentException("obj 为 null");
        return JsonBuilder.toPrettyJsonString(obj);
    }

    // ====== 路径查询 ======

    /**
     * 在解析后的 JSON 对象上执行 JSONPath 表达式查询。
     *
     * @param root 根对象（可为 {@link JsonValue} 或原生 {@code Map}/{@code List}）
     * @param path JSONPath 表达式，如 {@code "$.key1.key2[0]"}
     * @return 查询结果，路径不存在时返回 null；多个结果时返回 List
     * @see JsonPath#eval(Object, String)
     */
    public static Object eval(Object root, String path) {
        return JsonPath.eval(root, path);
    }

    /**
     * 在解析后的 JSON 对象上执行 JSONPath 表达式查询，始终返回结果列表。
     *
     * @param root 根对象
     * @param path JSONPath 表达式
     * @return 匹配节点的值列表（永不 null）
     * @see JsonPath#evalAll(Object, String)
     */
    public static List<Object> evalAll(Object root, String path) {
        return JsonPath.evalAll(root, path);
    }
}
