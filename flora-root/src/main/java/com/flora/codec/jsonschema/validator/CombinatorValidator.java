package com.flora.codec.jsonschema.validator;

import com.flora.codec.json.JsonObject;
import com.flora.codec.json.JsonValue;
import com.flora.codec.jsonschema.CompiledSchema;
import com.flora.codec.jsonschema.SchemaRegistry;
import com.flora.codec.jsonschema.ValidationContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 组合关键字校验：{@code allOf}/{@code anyOf}/{@code oneOf}/{@code not}/{@code if}/{@code then}/{@code else}。
 * <p>anyOf/oneOf/not/if 用独立求值状态尝试分支，成功的分支求值合并回父上下文；
 * 失败分支的错误被丢弃。求值传播服务于 {@code unevaluated*} 关键字。</p>
 */
public final class CombinatorValidator implements KeywordValidator {

    private final List<CompiledSchema> allOf;
    private final List<CompiledSchema> anyOf;
    private final List<CompiledSchema> oneOf;
    private final CompiledSchema not;
    private final CompiledSchema ifSchema;
    private final CompiledSchema thenSchema;
    private final CompiledSchema elseSchema;

    private CombinatorValidator(List<CompiledSchema> allOf, List<CompiledSchema> anyOf,
                                List<CompiledSchema> oneOf, CompiledSchema not,
                                CompiledSchema ifSchema, CompiledSchema thenSchema, CompiledSchema elseSchema) {
        this.allOf = allOf;
        this.anyOf = anyOf;
        this.oneOf = oneOf;
        this.not = not;
        this.ifSchema = ifSchema;
        this.thenSchema = thenSchema;
        this.elseSchema = elseSchema;
    }

    public static CombinatorValidator of(JsonObject schema, SchemaRegistry registry, String baseUri) {
        return new CombinatorValidator(
                compileList(schema.get("allOf"), registry, baseUri),
                compileList(schema.get("anyOf"), registry, baseUri),
                compileList(schema.get("oneOf"), registry, baseUri),
                compileSingle(schema.get("not"), registry, baseUri),
                compileSingle(schema.get("if"), registry, baseUri),
                compileSingle(schema.get("then"), registry, baseUri),
                compileSingle(schema.get("else"), registry, baseUri));
    }

    @Override
    public void validate(Object instance, ValidationContext ctx) {
        if (allOf != null) {
            for (int i = 0; i < allOf.size(); i++) {
                allOf.get(i).validate(instance, ctx.childIndex(i, "allOf/" + i));
            }
        }
        if (anyOf != null) {
            validateAnyOf(instance, ctx);
        }
        if (oneOf != null) {
            validateOneOf(instance, ctx);
        }
        if (not != null) {
            validateNot(instance, ctx);
        }
        if (ifSchema != null) {
            validateIfThenElse(instance, ctx);
        }
    }

    private void validateAnyOf(Object instance, ValidationContext ctx) {
        int before = ctx.errorCount();
        boolean passed = false;
        for (int i = 0; i < anyOf.size(); i++) {
            int branchStart = ctx.errorCount();
            ValidationContext branch = ctx.forBranch();
            anyOf.get(i).validate(instance, branch.childIndex(i, "anyOf/" + i));
            if (branch.errorCount() == branchStart) {
                passed = true;
                ctx.evaluation.merge(branch.evaluation);
            }
        }
        ctx.truncateErrors(before);
        if (!passed) {
            ctx.addError("anyOf", "没有任何分支通过");
        }
    }

    private void validateOneOf(Object instance, ValidationContext ctx) {
        int before = ctx.errorCount();
        int passed = 0;
        for (int i = 0; i < oneOf.size(); i++) {
            int branchStart = ctx.errorCount();
            ValidationContext branch = ctx.forBranch();
            oneOf.get(i).validate(instance, branch.childIndex(i, "oneOf/" + i));
            if (branch.errorCount() == branchStart) {
                passed++;
                ctx.evaluation.merge(branch.evaluation);
            }
        }
        ctx.truncateErrors(before);
        if (passed != 1) {
            ctx.addError("oneOf", "通过的分支数 " + passed + "，期望恰好 1 个");
        }
    }

    private void validateNot(Object instance, ValidationContext ctx) {
        int before = ctx.errorCount();
        ValidationContext branch = ctx.forBranch();
        not.validate(instance, branch.childIndex(0, "not"));
        boolean subPassed = branch.errorCount() > before;
        ctx.truncateErrors(before); // 丢弃子 schema 尝试错误
        if (!subPassed) {
            ctx.addError("not", "实例不满足 not 的约束");
        }
    }

    private void validateIfThenElse(Object instance, ValidationContext ctx) {
        int before = ctx.errorCount();
        ValidationContext ifBranch = ctx.forBranch();
        ifSchema.validate(instance, ifBranch.childIndex(0, "if"));
        boolean ifPassed = ifBranch.errorCount() == before; // 无错误 → if 通过
        ctx.truncateErrors(before); // if 是条件，其错误一律丢弃
        if (ifPassed) {
            ctx.evaluation.merge(ifBranch.evaluation);
            if (thenSchema != null) {
                thenSchema.validate(instance, ctx.childIndex(0, "then"));
            }
        } else if (elseSchema != null) {
            elseSchema.validate(instance, ctx.childIndex(0, "else"));
        }
    }

    private static List<CompiledSchema> compileList(JsonValue value, SchemaRegistry registry, String baseUri) {
        if (value == null || !value.isArray()) {
            return null;
        }
        List<CompiledSchema> result = new ArrayList<>();
        for (JsonValue item : value.asArray().elements()) {
            result.add(registry.compileNode(item.toNative(), baseUri));
        }
        return result;
    }

    private static CompiledSchema compileSingle(JsonValue value, SchemaRegistry registry, String baseUri) {
        return value == null ? null : registry.compileNode(value.toNative(), baseUri);
    }
}
