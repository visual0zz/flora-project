package com.flora.mock.jsonschema;

import com.flora.codec.json.JsonBuilder;
import com.flora.codec.json.JsonParser;
import com.flora.mock.jsonschema.impl.GenerationContext;
import com.flora.mock.jsonschema.impl.GenerationNode;
import com.flora.mock.jsonschema.impl.GeneratorCompiler;
import com.flora.mock.jsonschema.impl.RandomSupport;
import com.flora.tag.ThreadFragile;

import java.util.Random;
import java.util.random.RandomGenerator;

/**
 * JSON Schema 数据生成器门面。
 * <p>根据 2020-12 schema 生成随机且（尽力）符合约束的 JSON 实例。
 * 输入可为 JSON 字符串或已解析的 JSON Object（Map/List 嵌套）；
 * {@link #generate()} 返回 Map/List 嵌套对象，{@link #generateStr()} 返回 JSON 字符串。
 * 熵源通过 {@link #of(Object, RandomGenerator)} 注入，同一种子生成结果可复现。</p>
 *
 * <p><b>支持的语法</b>：
 * 类型 {@code type}（object/array/string/integer/number/boolean/null）、
 * {@code enum}/{@code const}、数值范围 {@code minimum/maximum/exclusiveMinimum/exclusiveMaximum}
 * 与 {@code multipleOf}、字符串长度 {@code minLength/maxLength}、{@code pattern}
 * （委托 {@code RegexStringGenerator}，支持 {@code format} 逆向生成）、
 * 数组 {@code prefixItems/items/contains/uniqueItems/minItems/maxItems}、
 * 对象 {@code properties/required/dependentRequired/patternProperties/additionalProperties/
 * minProperties/maxProperties}、组合 {@code anyOf/oneOf/if-then-else}、
 * {@code allOf}（编译期合并常用约束交集，多个 {@code pattern} 用自动机交集）、
 * {@code $ref}/{@code $defs} 递归（深度由推荐长度预算驱动，截断时生成满足
 * required/minItems/minLength 等硬约束的最小实例）。</p>
 *
 * <p><b>不支持的语法</b>（忽略或尽力，不保证严格满足）：{@code not}、
 * {@code dependentSchemas}、{@code propertyNames}、{@code unevaluatedProperties/
 * unevaluatedItems}、{@code minContains/maxContains}（仅生成单个 contains 元素）；
 * 复杂 {@code if/then/else} 条件（随机走分支，不保证 if 前提成立）、
 * {@code pattern} 与 {@code minLength/maxLength} 冲突（以 pattern 结构为准，长度可能越界）、
 * {@code oneOf} 非互斥分支（可能同时满足多个）、{@code allOf} 中未覆盖的约束组合
 * （取交集近似）、递归截断层（不保证最深层非 required 的可选约束）。</p>
 *
 * <pre>{@code
 * JsonGenerator generator = JsonGenerator.of("{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}}}");
 * Object instance = generator.generate();      // Map/List 嵌套对象
 * String json = generator.generateStr();       // JSON 字符串
 * }</pre>
 */
@ThreadFragile("内部缓存 IdentityHashMap 在惰性编译时写入，共享熵源非线程安全，多线程并发 generate() 需外部同步")
public final class JsonGenerator {

    /** 默认推荐长度：生成实例的规模感目标。 */
    public static final int DEFAULT_TARGET_LENGTH = 64;

    private final GeneratorCompiler compiler;
    private final GenerationNode root;
    private final int targetLength;
    private final RandomGenerator entropy;

    private JsonGenerator(Object schemaObject, int targetLength, RandomGenerator entropy) {
        this.compiler = GeneratorCompiler.of(schemaObject);
        this.root = compiler.root();
        this.targetLength = targetLength;
        this.entropy = entropy;
    }

    /** 从 JSON 字符串构建生成器，使用默认推荐长度。 */
    public static JsonGenerator of(String schemaJson) {
        return of(JsonParser.parse(schemaJson), DEFAULT_TARGET_LENGTH, null);
    }

    /** 从 JSON 字符串构建生成器，指定推荐长度。 */
    public static JsonGenerator of(String schemaJson, int targetLength) {
        return of(JsonParser.parse(schemaJson), targetLength, null);
    }

    /** 从 JSON 字符串构建生成器，注入熵源（同一种子可复现）。 */
    public static JsonGenerator of(String schemaJson, RandomGenerator entropy) {
        return of(JsonParser.parse(schemaJson), DEFAULT_TARGET_LENGTH, entropy);
    }

    /** 从解析后的 Java 对象构建生成器，使用默认推荐长度。 */
    public static JsonGenerator of(Object schemaObject) {
        return of(schemaObject, DEFAULT_TARGET_LENGTH, null);
    }

    /** 从解析后的 Java 对象构建生成器，指定推荐长度。 */
    public static JsonGenerator of(Object schemaObject, int targetLength) {
        return of(schemaObject, targetLength, null);
    }

    /** 从解析后的 Java 对象构建生成器，注入熵源（同一种子可复现）。 */
    public static JsonGenerator of(Object schemaObject, RandomGenerator entropy) {
        return of(schemaObject, DEFAULT_TARGET_LENGTH, entropy);
    }

    /** 从解析后的 Java 对象构建生成器，指定推荐长度与熵源。 */
    public static JsonGenerator of(Object schemaObject, int targetLength, RandomGenerator entropy) {
        return new JsonGenerator(schemaObject, targetLength, entropy);
    }

    /** 生成随机实例（Map/List 嵌套结构）。 */
    public Object generate() {
        RandomGenerator source = entropy != null ? entropy : new Random();
        return root.generate(new GenerationContext(new RandomSupport(source), targetLength));
    }

    /** 生成随机实例并序列化为紧凑 JSON 字符串。 */
    public String generateStr() {
        return JsonBuilder.toJsonString(generate());
    }
}
