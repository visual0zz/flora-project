package com.flora.codec.json.model;

import com.flora.codec.json.JsonBuilder;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * JSON 数字值，包裹 {@link Number}（解析器按精度产出 {@code Long} / {@code BigDecimal} / {@code BigInteger}）。
 * <p>提供向各数字类型的无损/近似取值；比较基于 {@link BigDecimal} 数值，避免整型与浮点混比出错。</p>
 */
public final class JsonNumber implements JsonValue {

    private final Number value;

    public JsonNumber(Number value) {
        this.value = value;
    }

    /** 取得包裹的数字（原类型，可能是 Long / BigDecimal / BigInteger）。 */
    public Number value() {
        return value;
    }

    @Override
    public boolean isNumber() {
        return true;
    }

    @Override
    public JsonNumber asNumber() {
        return this;
    }

    @Override
    public Object toNative() {
        return value;
    }

    @Override
    public String toJsonString() {
        return JsonBuilder.toJsonString(value);
    }

    @Override
    public String toPrettyString() {
        return JsonBuilder.toPrettyJsonString(value);
    }

    @Override
    public String typeName() {
        return "number";
    }

    /** 以 {@code long} 形式取值；非整数表示时抛异常。 */
    public long longValue() {
        return value.longValue();
    }

    /** 以 {@code int} 形式取值。 */
    public int intValue() {
        return value.intValue();
    }

    /** 以 {@code short} 形式取值。 */
    public short shortValue() {
        return value.shortValue();
    }

    /** 以 {@code byte} 形式取值。 */
    public byte byteValue() {
        return value.byteValue();
    }

    /** 以 {@code float} 形式取值（可能损失精度）。 */
    public float floatValue() {
        return value.floatValue();
    }

    /** 以 {@code double} 形式取值（可能损失精度）。 */
    public double doubleValue() {
        return value.doubleValue();
    }

    /** 以 {@link BigDecimal} 形式取值，保证精确。 */
    public BigDecimal decimalValue() {
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof BigInteger) return new BigDecimal((BigInteger) value);
        if (value instanceof Double || value instanceof Float) {
            return BigDecimal.valueOf(value.doubleValue());
        }
        return BigDecimal.valueOf(value.longValue());
    }

    /** 是否为整数值（无小数部分）。 */
    public boolean isIntegral() {
        return decimalValue().scale() <= 0;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof JsonNumber)) return false;
        return decimalValue().compareTo(((JsonNumber) o).decimalValue()) == 0;
    }

    @Override
    public int hashCode() {
        return decimalValue().hashCode();
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
