package com.flora.root.codec.jsonschema.validator;

import com.flora.root.codec.json.model.JsonObject;
import com.flora.root.codec.jsonschema.JsonTypes;
import com.flora.root.codec.jsonschema.impl.SchemaNumbers;
import com.flora.root.codec.jsonschema.impl.ValidationContext;

import java.math.BigDecimal;

/**
 * 数值关键字校验：{@code minimum}/{@code maximum}/{@code exclusiveMinimum}/
 * {@code exclusiveMaximum}/{@code multipleOf}（2020-12 语义，exclusive 为布尔排他边界）。
 * <p>仅作用于数字实例，非数字跳过。比较统一转 {@link BigDecimal} 用 {@code compareTo}。</p>
 */
public final class NumericValidator implements KeywordValidator {

    private final BigDecimal minimum;
    private final BigDecimal maximum;
    private final BigDecimal exclusiveMinimum;
    private final BigDecimal exclusiveMaximum;
    private final BigDecimal multipleOf;

    private NumericValidator(BigDecimal min, BigDecimal max, BigDecimal exMin, BigDecimal exMax, BigDecimal mult) {
        this.minimum = min;
        this.maximum = max;
        this.exclusiveMinimum = exMin;
        this.exclusiveMaximum = exMax;
        this.multipleOf = mult;
    }

    public static NumericValidator of(JsonObject schema) {
        return new NumericValidator(
                SchemaNumbers.decimalOf(schema.getNumber("minimum")),
                SchemaNumbers.decimalOf(schema.getNumber("maximum")),
                SchemaNumbers.decimalOf(schema.getNumber("exclusiveMinimum")),
                SchemaNumbers.decimalOf(schema.getNumber("exclusiveMaximum")),
                SchemaNumbers.decimalOf(schema.getNumber("multipleOf")));
    }

    @Override
    public void validate(Object instance, ValidationContext ctx) {
        BigDecimal value = JsonTypes.decimalOf(instance);
        if (value == null) {
            return; // 非数字
        }
        if (minimum != null && value.compareTo(minimum) < 0) {
            ctx.addError("minimum", "值 " + value + " 小于 minimum " + minimum);
        }
        if (maximum != null && value.compareTo(maximum) > 0) {
            ctx.addError("maximum", "值 " + value + " 大于 maximum " + maximum);
        }
        if (exclusiveMinimum != null && value.compareTo(exclusiveMinimum) <= 0) {
            ctx.addError("exclusiveMinimum", "值 " + value + " 不大于 exclusiveMinimum " + exclusiveMinimum);
        }
        if (exclusiveMaximum != null && value.compareTo(exclusiveMaximum) >= 0) {
            ctx.addError("exclusiveMaximum", "值 " + value + " 不小于 exclusiveMaximum " + exclusiveMaximum);
        }
        if (multipleOf != null && multipleOf.compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal remainder = value.remainder(multipleOf);
            if (remainder.compareTo(BigDecimal.ZERO) != 0) {
                ctx.addError("multipleOf", "值 " + value + " 不是 " + multipleOf + " 的倍数");
            }
        }
    }

}
