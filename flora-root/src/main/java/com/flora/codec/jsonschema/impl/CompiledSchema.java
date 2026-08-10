package com.flora.codec.jsonschema.impl;

import com.flora.codec.jsonschema.validator.KeywordValidator;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个 schema 节点的编译结果。节点只编译一次，校验时依次执行关键字校验器。
 * <p>编译期可变（收集校验器），支持递归 $ref 的循环引用——节点先入缓存，
 * 校验器引用同一对象；编译完成后 {@link #freeze()} 冻结，此后 {@link #add(KeywordValidator)}
 * 将被拒绝，防止校验阶段误操作已完成的编译结果。</p>
 */
public final class CompiledSchema {

    private static final CompiledSchema ALWAYS = new CompiledSchema("", true, false, true);
    private static final CompiledSchema NEVER = new CompiledSchema("", false, true, true);

    public final String baseUri;
    private final List<KeywordValidator> validators = new ArrayList<>();
    private final boolean alwaysValid;
    private final boolean alwaysInvalid;
    private boolean frozen;

    private CompiledSchema(String baseUri, boolean alwaysValid, boolean alwaysInvalid, boolean frozen) {
        this.baseUri = baseUri;
        this.alwaysValid = alwaysValid;
        this.alwaysInvalid = alwaysInvalid;
        this.frozen = frozen;
    }

    private CompiledSchema(String baseUri) {
        this(baseUri, false, false, false);
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
        if (frozen) {
            throw new IllegalStateException("schema 已冻结，不可再添加校验器");
        }
        validators.add(validator);
    }

    /** 冻结本节点，禁止后续添加校验器（递归 $ref 循环防护下，节点入缓存后即冻结）。 */
    public void freeze() {
        frozen = true;
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
