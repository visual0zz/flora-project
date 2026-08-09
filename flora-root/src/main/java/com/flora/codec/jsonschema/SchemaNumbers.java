package com.flora.codec.jsonschema;

import com.flora.codec.json.JsonNumber;
import com.flora.codec.json.JsonValue;

import java.math.BigDecimal;

/**
 * schema 编译期数值/布尔取值助手，供各关键字校验器复用。
 * <p>统一处理 {@link JsonValue} 到目标类型的空安全转换，避免 ArrayValidator/
 * ObjectValidator 等重复实现 {@code intOf}/{@code boolOf} 之类的工具方法。</p>
 */
public final class SchemaNumbers {

    private SchemaNumbers() {
    }

    /** 取 {@code int}；值缺失或非数字时返回 {@code null}。 */
    public static Integer intOf(JsonValue v) {
        if (v == null || !v.isNumber()) {
            return null;
        }
        return v.asNumber().intValue();
    }

    /** 取 {@code boolean}；值缺失或非布尔时返回 {@code false}。 */
    public static boolean boolOf(JsonValue v) {
        return v != null && v.isBool() && v.asBool();
    }

    /** 取 {@link BigDecimal}；值缺失或非数字时返回 {@code null}。 */
    public static BigDecimal decimalOf(JsonValue v) {
        if (v == null || !v.isNumber()) {
            return null;
        }
        return v.asNumber().decimalValue();
    }
}
