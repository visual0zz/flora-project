package com.flora.codec.jsonschema;

import com.flora.codec.json.JsonParser;

import java.util.List;

/**
 * JSON Schema 2020-12 校验器门面。
 * <p>从 schema（Java 对象或 JSON 字符串）构建，可反复校验多个实例。
 * 完整支持 2020-12 关键字（含 unevaluatedProperties/unevaluatedItems）、
 * {@code $ref}/{@code $defs} 引用、以及常用 {@code format} 校验。</p>
 *
 * <pre>{@code
 * JsonSchema schema = JsonSchema.of("{\"type\":\"object\",\"required\":[\"name\"]}");
 * boolean ok = schema.isValid(instance);
 * List<ValidationError> errors = schema.validate(instance).errors();
 * }</pre>
 */
public final class JsonSchema {

    private final SchemaRegistry registry;
    private final CompiledSchema root;

    private JsonSchema(SchemaRegistry registry) {
        this.registry = registry;
        this.root = registry.root();
    }

    /** 从解析后的 Java 对象构建 schema。 */
    public static JsonSchema of(Object schemaObject) {
        return new JsonSchema(SchemaRegistry.of(schemaObject));
    }

    /** 从 JSON 字符串构建 schema。 */
    public static JsonSchema of(String schemaJson) {
        return of(JsonParser.parse(schemaJson));
    }

    /** 校验实例，返回是否通过。 */
    public boolean isValid(Object instance) {
        return validate(instance).isValid();
    }

    /** 校验实例，返回完整结果（含错误列表）。 */
    public ValidationResult validate(Object instance) {
        ValidationContext ctx = new ValidationContext(registry);
        root.validate(instance, ctx);
        if (ctx.errorCount() == 0) {
            return ValidationResult.valid();
        }
        return ValidationResult.invalid(ctx.snapshotErrors());
    }

    /** 校验实例，返回错误列表（空表示通过）。 */
    public List<ValidationError> errors(Object instance) {
        return validate(instance).errors();
    }
}
