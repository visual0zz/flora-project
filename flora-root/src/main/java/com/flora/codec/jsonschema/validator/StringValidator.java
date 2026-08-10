package com.flora.codec.jsonschema.validator;

import com.flora.codec.json.JsonObject;
import com.flora.codec.jsonschema.impl.ValidationContext;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 字符串关键字校验：{@code minLength}/{@code maxLength}/{@code pattern}。
 * <p>长度按 UTF-16 码元计数（JSON Schema 语义）；{@code pattern} 采用
 * 未锚定（search）匹配语义，对应 ECMA-262 正则。</p>
 */
public final class StringValidator implements KeywordValidator {

    private final Integer minLength;
    private final Integer maxLength;
    private final Pattern pattern;

    private StringValidator(Integer minLength, Integer maxLength, Pattern pattern) {
        this.minLength = minLength;
        this.maxLength = maxLength;
        this.pattern = pattern;
    }

    public static StringValidator of(JsonObject schema) {
        Integer min = schema.getNumber("minLength") != null ? schema.getNumber("minLength").intValue() : null;
        Integer max = schema.getNumber("maxLength") != null ? schema.getNumber("maxLength").intValue() : null;
        Pattern p = null;
        String ps = schema.getString("pattern");
        if (ps != null) {
            try {
                p = Pattern.compile(ps);
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException("非法 pattern: " + ps, e);
            }
        }
        return new StringValidator(min, max, p);
    }

    @Override
    public void validate(Object instance, ValidationContext ctx) {
        if (!(instance instanceof String s)) {
            return; // 非字符串
        }
        if (minLength != null && s.length() < minLength) {
            ctx.addError("minLength", "字符串长度 " + s.length() + " 小于 " + minLength);
        }
        if (maxLength != null && s.length() > maxLength) {
            ctx.addError("maxLength", "字符串长度 " + s.length() + " 大于 " + maxLength);
        }
        if (pattern != null && !pattern.matcher(s).find()) {
            ctx.addError("pattern", "字符串不匹配 pattern: " + pattern.pattern());
        }
    }
}
