package com.flora.codec.jsonschema.validator;

import com.flora.codec.jsonschema.JsonTypes;
import com.flora.codec.jsonschema.ValidationContext;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * {@code type} 关键字校验（支持联合类型，如 {@code ["string","null"]}）。
 * <p>{@code integer} 匹配整数值（含无小数部分的 BigDecimal），是 {@code number} 的子类型。</p>
 */
public final class TypeValidator implements KeywordValidator {

    private final Set<String> types;
    private final boolean any;

    private TypeValidator(Set<String> types) {
        this.types = types;
        this.any = types.contains("number") || types.contains("integer");
    }

    public static TypeValidator of(Object type) {
        Set<String> set = new LinkedHashSet<>();
        if (type instanceof String s) {
            set.add(s);
        } else if (type instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof String s) {
                    set.add(s);
                }
            }
        }
        if (set.isEmpty()) {
            throw new IllegalArgumentException("type 必须是字符串或字符串数组: " + type);
        }
        return new TypeValidator(set);
    }

    @Override
    public void validate(Object instance, ValidationContext ctx) {
        String actual = JsonTypes.typeOf(instance);
        for (String expected : types) {
            if (matches(expected, actual, instance)) {
                return;
            }
        }
        ctx.addError("type", "期望类型 " + types + "，实际为 " + actual);
    }

    private static boolean matches(String expected, String actual, Object instance) {
        if (expected.equals(actual)) {
            return true;
        }
        if ("integer".equals(expected)) {
            return "number".equals(actual) && JsonTypes.isInteger(instance);
        }
        return false;
    }
}
