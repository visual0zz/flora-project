package com.flora.root.codec.jsonschema;

import com.flora.root.codec.json.model.JsonArray;
import com.flora.root.codec.json.model.JsonBool;
import com.flora.root.codec.json.model.JsonNull;
import com.flora.root.codec.json.model.JsonNumber;
import com.flora.root.codec.json.model.JsonObject;
import com.flora.root.codec.json.model.JsonString;
import com.flora.root.codec.json.model.JsonValue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * JSON 值类型判定与数值归一化工具。
 * <p>校验实例统一以 {@link JsonValue} 模型表达（object→{@link JsonObject}、
 * array→{@link JsonArray}、string→{@link JsonString}、number→{@link JsonNumber}、
 * boolean→{@link JsonBool}、null→{@link JsonNull}）。本工具在入口处经 {@link #unwrap(Object)}
 * 将 {@code JsonValue} 剥离为原生树（Map/List/标量）后再做类型判定，从而同时兼容
 * 直接传入原生值的内部调用（生成器工作副本）。数字比较统一转 {@link BigDecimal} 用 {@code compareTo}。</p>
 */
public final class JsonTypes {

    private JsonTypes() {
    }

    /**
     * 将 {@link JsonValue} 模型剥离为原生值：对象→{@code Map}、数组→{@code List}、
     * 标量→对应 Java 原生类型（{@code null}→{@code null}）。非 {@code JsonValue} 的原生值原样返回。
     */
    public static Object unwrap(Object value) {
        if (value instanceof JsonValue jv) {
            return jv.toNative();
        }
        return value;
    }

    public static String typeOf(Object value) {
        value = unwrap(value);
        if (value == null) {
            return "null";
        }
        if (value instanceof Map) {
            return "object";
        }
        if (value instanceof List) {
            return "array";
        }
        if (value instanceof String) {
            return "string";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof Long || value instanceof BigInteger
                || value instanceof BigDecimal || value instanceof Double
                || value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return "number";
        }
        return "unknown";
    }

    /** 是否整数值（type: "integer" 匹配，包括无小数部分的 BigDecimal）。 */
    public static boolean isInteger(Object value) {
        value = unwrap(value);
        if (value instanceof Long || value instanceof BigInteger
                || value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return true;
        }
        if (value instanceof BigDecimal bd) {
            return bd.stripTrailingZeros().scale() <= 0;
        }
        if (value instanceof Double d) {
            return d == Math.floor(d) && !Double.isInfinite(d);
        }
        return false;
    }

    /** 归一化为 BigDecimal（非数字返回 null）。 */
    public static BigDecimal decimalOf(Object value) {
        value = unwrap(value);
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Long l) {
            return BigDecimal.valueOf(l);
        }
        if (value instanceof Integer i) {
            return BigDecimal.valueOf(i);
        }
        if (value instanceof Short s) {
            return BigDecimal.valueOf(s);
        }
        if (value instanceof Byte b) {
            return BigDecimal.valueOf(b);
        }
        if (value instanceof BigInteger bi) {
            return new BigDecimal(bi);
        }
        if (value instanceof Double d) {
            return BigDecimal.valueOf(d);
        }
        return null;
    }

    /** 深度 JSON 等价（数字按 BigDecimal 比较，供 uniqueItems 使用）。 */
    public static boolean deepEquals(Object a, Object b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        a = unwrap(a);
        b = unwrap(b);
        BigDecimal da = decimalOf(a);
        BigDecimal db = decimalOf(b);
        if (da != null && db != null) {
            return da.compareTo(db) == 0;
        }
        if (a instanceof Map<?, ?> ma && b instanceof Map<?, ?> mb) {
            if (ma.size() != mb.size()) {
                return false;
            }
            for (Map.Entry<?, ?> e : ma.entrySet()) {
                if (!mb.containsKey(e.getKey()) || !deepEquals(e.getValue(), mb.get(e.getKey()))) {
                    return false;
                }
            }
            return true;
        }
        if (a instanceof List<?> la && b instanceof List<?> lb) {
            if (la.size() != lb.size()) {
                return false;
            }
            for (int i = 0; i < la.size(); i++) {
                if (!deepEquals(la.get(i), lb.get(i))) {
                    return false;
                }
            }
            return true;
        }
        return a.equals(b);
    }
}
