package com.flora.codec.jsonschema.validator;

import com.flora.codec.jsonschema.ValidationContext;
import com.flora.codec.jsonschema.format.FormatValidators;

import java.util.function.Predicate;

/**
 * {@code format} 关键字校验（严格模式）。
 * <p>未知格式在 schema 编译期抛错；只对字符串实例生效。</p>
 */
public final class FormatValidator implements KeywordValidator {

    private final String format;
    private final Predicate<String> predicate;

    private FormatValidator(String format) {
        this.format = format;
        this.predicate = FormatValidators.get(format);
    }

    public static FormatValidator of(String format) {
        if (!FormatValidators.isKnown(format)) {
            throw new IllegalArgumentException("未知的 format: " + format);
        }
        return new FormatValidator(format);
    }

    @Override
    public void validate(Object instance, ValidationContext ctx) {
        if (!(instance instanceof String s)) {
            return; // format 只作用于字符串
        }
        if (!predicate.test(s)) {
            ctx.addError("format", "字符串不符合 format " + format + ": " + s);
        }
    }
}
