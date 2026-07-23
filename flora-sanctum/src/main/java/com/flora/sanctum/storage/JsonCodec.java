package com.flora.sanctum.storage;

import com.flora.codec.JsonUtil;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * JSON 编解码门面。
 * <p>
 * 复用 flora-root 的 {@link JsonUtil}，提供类型安全的转换辅助。
 */
public final class JsonCodec {

    private JsonCodec() {
    }

    /** 将对象序列化为 JSON 字符串。 */
    public static String toJson(Object obj) {
        return JsonUtil.toPrettyJsonString(obj);
    }

    /** 解析 JSON 字符串为 Map。 */
    public static Map<String, Object> parseObject(String json) {
        return JsonUtil.parseObject(json);
    }

    /** 解析 JSON 字符串为 List。 */
    public static List<Object> parseArray(String json) {
        return JsonUtil.parseArray(json);
    }

    /**
     * 安全地获取 Map 中的字符串值。
     *
     * @param map  Map
     * @param key  键
     * @param def  默认值
     * @return 字符串值，如果不存在或类型不匹配返回默认值
     */
    public static String getString(Map<String, Object> map, String key, String def) {
        Object v = map.get(key);
        return v instanceof String s ? s : def;
    }

    /**
     * 安全地获取 Map 中的 int 值。
     */
    public static int getInt(Map<String, Object> map, String key, int def) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.intValue();
        return def;
    }

    /**
     * 安全地获取 Map 中的 long 值。
     */
    public static long getLong(Map<String, Object> map, String key, long def) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.longValue();
        return def;
    }

    /**
     * 安全地获取 Map 中的 List&lt;Map&lt;String, Object&gt;&gt; 值。
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> getObjectList(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map) {
            return (List<Map<String, Object>>) list;
        }
        return Collections.emptyList();
    }
}
