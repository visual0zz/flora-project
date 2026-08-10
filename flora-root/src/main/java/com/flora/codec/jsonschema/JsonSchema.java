package com.flora.codec.jsonschema;

import com.flora.codec.json.JsonObject;
import com.flora.codec.json.JsonParser;
import com.flora.codec.jsonschema.impl.CompiledSchema;
import com.flora.codec.jsonschema.impl.SchemaRegistry;
import com.flora.codec.jsonschema.impl.ValidationContext;

import java.util.List;

/**
 * JSON Schema 2020-12 校验器门面。
 * <p>从 schema（Java 对象或 JSON 字符串）构建，可反复校验多个实例。</p>
 *
 * <p><b>支持的语法</b>：
 * 类型 {@code type}、{@code enum}/{@code const}、数值 {@code minimum/maximum/
 * exclusiveMinimum/exclusiveMaximum/multipleOf}、字符串 {@code minLength/maxLength/pattern}
 * （pattern 用 JDK {@code java.util.regex} 搜索语义）与 {@code format} 常用格式校验、
 * 数组 {@code prefixItems/items/contains/uniqueItems/minItems/maxItems/minContains/maxContains}、
 * 对象 {@code properties/required/patternProperties/additionalProperties/
 * minProperties/maxProperties/dependentRequired/dependentSchemas/propertyNames}、
 * 组合 {@code allOf/anyOf/oneOf/not/if-then-else}、
 * {@code $ref}/{@code $defs} 引用、
 * {@code unevaluatedProperties/unevaluatedItems}（完整求值语义）。</p>
 *
 * <p><b>支持的 format</b>：date/date-time/time/email/idn-email/hostname/idn-hostname/
 * ipv4/ipv6/uri/uri-reference/iri/iri-reference/uuid/regex/json-pointer/
 * relative-json-pointer/duration。</p>
 *
 * <p><b>正则校验说明</b>：{@code pattern} 关键字使用 JDK {@code java.util.regex}
 * 全特性（含环视/反向引用/命名组），语义为 ECMA-262 搜索（{@code find()}，
 * 字符串任意位置命中即通过），与 {@code FormatValidators} 的 {@code format: "regex"}
 * 校验一致。</p>
 *
 * <p><b>不支持的语法</b>：未知 {@code format} 在 schema 编译期抛错（严格模式）；
 * 超出上述列表的关键字被忽略（2020-12 未识别的扩展关键字不报错）。</p>
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
        return of(JsonParser.parseObject(schemaJson));
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
