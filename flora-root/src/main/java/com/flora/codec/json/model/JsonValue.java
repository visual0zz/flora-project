package com.flora.codec.json.model;

import java.util.Map;

/**
 * JSON 值模型的根接口，统一表达 JSON 的六种类型（object / array / string / number / boolean / null）。
 * <p>解析器 {@link com.flora.codec.json.JsonParser} 直接产出实现本接口的类型，序列化器
 * {@link com.flora.codec.json.JsonBuilder} 与路径引擎 {@link com.flora.codec.json.path.JsonPath}
 * 均基于本接口遍历与读写，使 {@code JsonValue} 成为整个 JSON 工具的内部核心数据。</p>
 * <p>各实现提供类型谓词（{@link #isObject()} 等）与强类型取值（{@link #asObject()} 等），
 * 取值不符合当前类型时抛出 {@link IllegalStateException}。标量值（{@link JsonString} /
 * {@link JsonNumber} / {@link JsonBool} / {@link JsonNull}）直接包裹原生 Java 类型，
 * 不额外引入对象包装开销。</p>
 */
public interface JsonValue {

    /** 是否为 JSON Object。 */
    default boolean isObject() {
        return false;
    }

    /** 是否为 JSON Array。 */
    default boolean isArray() {
        return false;
    }

    /** 是否为 JSON String。 */
    default boolean isString() {
        return false;
    }

    /** 是否为 JSON Number。 */
    default boolean isNumber() {
        return false;
    }

    /** 是否为 JSON Boolean。 */
    default boolean isBool() {
        return false;
    }

    /** 是否为 JSON null。 */
    default boolean isNull() {
        return false;
    }

    /** 强类型取得 JSON Object，非 object 时抛异常。 */
    default JsonObject asObject() {
        throw new IllegalStateException("当前值不是 JSON Object: " + typeName());
    }

    /** 强类型取得 JSON Array，非 array 时抛异常。 */
    default JsonArray asArray() {
        throw new IllegalStateException("当前值不是 JSON Array: " + typeName());
    }

    /** 强类型取得字符串，非 string 时抛异常。 */
    default String asString() {
        throw new IllegalStateException("当前值不是 JSON String: " + typeName());
    }

    /** 强类型取得数字，非 number 时抛异常。 */
    default JsonNumber asNumber() {
        throw new IllegalStateException("当前值不是 JSON Number: " + typeName());
    }

    /** 强类型取得布尔，非 boolean 时抛异常。 */
    default boolean asBool() {
        throw new IllegalStateException("当前值不是 JSON Boolean: " + typeName());
    }

    /** 返回适合放入 {@code Map<String, Object>} / {@code List<Object>} 原生树的 Java 值（对象/列表递归展开）。 */
    Object toNative();

    /** 取得原生 {@code Map} 视图（仅 object 类型可用，否则抛异常）。 */
    default Map<String, Object> toMap() {
        throw new IllegalStateException("当前值不是 JSON Object: " + typeName());
    }

    /** 序列化为紧凑 JSON 字符串。 */
    String toJsonString();

    /** 序列化为带缩进的美化 JSON 字符串。 */
    String toPrettyString();

    /** 返回类型名（用于异常信息），如 {@code "object"} / {@code "string"}。 */
    String typeName();
}
