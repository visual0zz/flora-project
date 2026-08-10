package com.flora.sanctum.storage;

import com.flora.codec.JsonUtil;
import com.flora.codec.json.model.JsonArray;
import com.flora.codec.json.model.JsonObject;

/**
 * JSON 编解码门面。
 * <p>
 * 复用 flora-root 的 {@link JsonUtil}，提供类型安全的转换辅助。
 * 解析默认返回 {@link JsonObject} / {@link JsonArray} 核心模型，可通过其
 * {@code toMap()} / {@code toNative()} 转为原生树以适配既有 {@code Map} 契约。
 */
public final class JsonCodec {

    private JsonCodec() {
    }

    /** 将对象序列化为 JSON 字符串。 */
    public static String toJson(Object obj) {
        return JsonUtil.toPrettyJsonString(obj);
    }

    /** 解析 JSON 字符串为 JsonObject。 */
    public static JsonObject parseObject(String json) {
        return JsonUtil.parseObject(json);
    }

    /** 解析 JSON 字符串为 JsonArray。 */
    public static JsonArray parseArray(String json) {
        return JsonUtil.parseArray(json);
    }
}
