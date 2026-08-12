package com.flora.root.codec.jsonschema.validator;

import com.flora.root.codec.jsonschema.impl.CompiledSchema;
import com.flora.root.codec.jsonschema.impl.SchemaRegistry;
import com.flora.root.codec.jsonschema.impl.ValidationContext;

/**
 * {@code $ref}/{@code $dynamicRef} 引用校验。
 * <p>编译期解析引用目标（JSON Pointer / anchor / $id），运行时校验目标 schema。
 * {@code $dynamicRef} 简化为按普通引用解析。循环引用通过活动引用集合防护。</p>
 */
public final class RefValidator implements KeywordValidator {

    private final CompiledSchema target;
    private final String ref;

    private RefValidator(CompiledSchema target, String ref) {
        this.target = target;
        this.ref = ref;
    }

    public static RefValidator of(String ref, SchemaRegistry registry, String currentBase) {
        return new RefValidator(registry.resolve(ref, currentBase), ref);
    }

    /** $dynamicRef：简化为静态引用解析。 */
    public static RefValidator dynamic(String ref, SchemaRegistry registry, String currentBase) {
        return new RefValidator(registry.resolve(ref, currentBase), ref);
    }

    @Override
    public void validate(Object instance, ValidationContext ctx) {
        if (ctx.isRefActive(ref)) {
            return; // 循环引用，跳过
        }
        ctx.enterRef(ref);
        try {
            target.validate(instance, ctx.childIndex(0, "$ref"));
        } finally {
            ctx.leaveRef(ref);
        }
    }
}
