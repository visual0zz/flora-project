package com.flora.codec.jsonschema.validator;

import com.flora.codec.jsonschema.JsonTypes;
import com.flora.codec.jsonschema.impl.ValidationContext;

import java.util.List;

/**
 * {@code enum} 与 {@code const} 关键字校验。
 * <p>值等价采用深度比较（数字按 BigDecimal 归一化，对象/数组递归）。</p>
 */
public final class EnumValidator implements KeywordValidator {

    private final String keyword;
    private final List<?> values;

    private EnumValidator(String keyword, List<?> values) {
        this.keyword = keyword;
        this.values = values;
    }

    public static EnumValidator enumOf(Object value) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("enum 必须是数组: " + value);
        }
        return new EnumValidator("enum", list);
    }

    public static EnumValidator constOf(Object value) {
        return new EnumValidator("const", List.of(value));
    }

    @Override
    public void validate(Object instance, ValidationContext ctx) {
        for (Object candidate : values) {
            if (JsonTypes.deepEquals(candidate, instance)) {
                return;
            }
        }
        ctx.addError(keyword, keyword + " 值不匹配: " + instance);
    }
}
