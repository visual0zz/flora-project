package com.flora.codec.json;

import java.util.List;
import java.util.Map;

/**
 * 原生 Java 值与 {@link JsonValue} 之间的桥梁，供容器类与解析器复用。
 * <p>约定：{@code null} → {@link JsonNull}；{@code String} → {@link JsonString}；
 * {@code Number} → {@link JsonNumber}；{@code Boolean} → {@link JsonBool}；
 * {@code JsonValue} 原样返回；{@code List} / 数组 → {@link JsonArray}；
 * {@code Map} → {@link JsonObject}。其余类型（Bean 等）不适合直接包裹为标量值，
 * 调用方应先经 {@link JsonObject#fromBean} 转换。</p>
 */
final class JsonConversions {

    private JsonConversions() {
    }

    /** 将原生值转为 {@link JsonValue}；已为 JsonValue 则原样返回。 */
    static JsonValue toValue(Object value) {
        if (value == null) return JsonNull.INSTANCE;
        if (value instanceof JsonValue) return (JsonValue) value;
        if (value instanceof String) return new JsonString((String) value);
        if (value instanceof Boolean) return new JsonBool((Boolean) value);
        if (value instanceof Number) return new JsonNumber((Number) value);
        if (value instanceof Character) return new JsonString(value.toString());
        if (value instanceof Enum) return new JsonString(((Enum<?>) value).name());
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) value;
            return JsonObject.fromMap(m);
        }
        if (value instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> l = (List<Object>) value;
            return JsonArray.fromList(l);
        }
        if (value.getClass().isArray()) {
            return JsonArray.fromArray(value);
        }
        throw new IllegalArgumentException("无法将类型 " + value.getClass().getName() + " 直接转为 JsonValue");
    }

    /** 将 {@link JsonValue} 还原为原生值（深拷贝展开容器）。 */
    static Object toNative(JsonValue value) {
        return value.toNative();
    }
}
