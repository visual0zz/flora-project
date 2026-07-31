package com.flora.codec.jsonschema;

import com.flora.codec.jsonschema.validator.KeywordValidator;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个 schema 节点的编译结果。节点只编译一次，校验时依次执行关键字校验器。
 * <p>编译期可变（收集校验器），支持递归 $ref 的循环引用——节点先入缓存，
 * 校验器引用同一对象，编译完成后 {@link #freeze()} 冻结。</p>
 */
public final class CompiledSchema {

    private static final CompiledSchema ALWAYS = new CompiledSchema("", true, false);
    private static final CompiledSchema NEVER = new CompiledSchema("", false, true);

    public final String baseUri;
    private final List<KeywordValidator> validators = new ArrayList<>();
    private final boolean alwaysValid;
    private final boolean alwaysInvalid;

    private CompiledSchema(String baseUri, boolean alwaysValid, boolean alwaysInvalid) {
        this.baseUri = baseUri;
        this.alwaysValid = alwaysValid;
        this.alwaysInvalid = alwaysInvalid;
    }

    private CompiledSchema(String baseUri) {
        this(baseUri, false, false);
    }

    public static CompiledSchema always() {
        return ALWAYS;
    }

    public static CompiledSchema never() {
        return NEVER;
    }

    public static CompiledSchema newSchema(String baseUri) {
        return new CompiledSchema(baseUri);
    }

    public void add(KeywordValidator validator) {
        validators.add(validator);
    }

    public void freeze() {
        // 冻结标记：编译完成后不可再添加校验器
    }

    public boolean isAlwaysValid() {
        return alwaysValid;
    }

    public void validate(Object instance, ValidationContext ctx) {
        if (alwaysValid) {
            return;
        }
        if (alwaysInvalid) {
            ctx.addError("false", "实例不匹配 false schema");
            return;
        }
        for (KeywordValidator validator : validators) {
            validator.validate(instance, ctx);
        }
    }
}
