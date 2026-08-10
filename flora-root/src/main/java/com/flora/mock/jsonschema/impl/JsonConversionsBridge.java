package com.flora.mock.jsonschema.impl;

import com.flora.codec.json.model.JsonArray;
import com.flora.codec.json.model.JsonBool;
import com.flora.codec.json.model.JsonNull;
import com.flora.codec.json.model.JsonNumber;
import com.flora.codec.json.model.JsonObject;
import com.flora.codec.json.model.JsonString;
import com.flora.codec.json.model.JsonValue;

import java.util.List;
import java.util.Map;

/**
 * 原生树（Map/List/标量）与 {@link JsonValue} 模型之间的桥接。
 * <p>{@code model.impl.JsonConversions} 未被导出，故在此用已导出的
 * {@link JsonObject#fromMap}/{@link JsonArray#fromList}（内部递归转换）搭建入口，
 * 供生成器将原生生成结果包装为 {@link JsonValue} 模型实例。</p>
 */
public final class JsonConversionsBridge {

    private JsonConversionsBridge() {
    }

    public static JsonValue toValue(Object o) {
        if (o instanceof JsonValue jv) {
            return jv;
        }
        if (o instanceof Map<?, ?> m) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) m;
            return JsonObject.fromMap(map);
        }
        if (o instanceof List<?> l) {
            return JsonArray.fromList(l);
        }
        if (o == null) {
            return JsonNull.INSTANCE;
        }
        if (o instanceof String s) {
            return new JsonString(s);
        }
        if (o instanceof Number n) {
            return new JsonNumber(n);
        }
        if (o instanceof Boolean b) {
            return new JsonBool(b);
        }
        if (o instanceof Character c) {
            return new JsonString(c.toString());
        }
        if (o instanceof Enum<?> e) {
            return new JsonString(e.name());
        }
        throw new IllegalArgumentException("无法将类型 " + o.getClass().getName() + " 包装为 JsonValue");
    }
}
