package com.flora.mock.jsonschema;

import com.flora.codec.json.JsonBuilder;
import com.flora.codec.json.JsonParser;

import java.util.Random;
import java.util.random.RandomGenerator;

/**
 * JSON Schema 数据生成器门面。
 * <p>根据 2020-12 schema 生成随机且（尽力）符合约束的 JSON 实例。
 * 支持：type/enum/const、数值范围/multipleOf、字符串长度/format/pattern、
 * 数组（prefixItems/items/contains/uniqueItems）、对象（properties/required/依赖）、
 * 组合（anyOf/oneOf/if-then-else/allOf 合并）、{@code $ref}/{@code $defs} 递归（深度限制）。</p>
 * <p>输入可为 JSON 字符串或已解析的 JSON Object（Map/List 嵌套）；
 * {@link #generate()} 返回 Map/List 嵌套对象，{@link #generateJson()} 返回 JSON 字符串。
 * 熵源通过 {@link #of(Object, RandomGenerator)} 注入，同一种子生成结果可复现。</p>
 *
 * <pre>{@code
 * JsonGenerator generator = JsonGenerator.of("{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}}}");
 * Object instance = generator.generate();      // Map/List 嵌套对象
 * String json = generator.generateJson();      // JSON 字符串
 * }</pre>
 */
public final class JsonGenerator {

    private final GeneratorCompiler compiler;
    private final GenerationNode root;
    private final GenerationConfig config;
    private final RandomGenerator entropy;

    private JsonGenerator(Object schemaObject, GenerationConfig config, RandomGenerator entropy) {
        this.compiler = GeneratorCompiler.of(schemaObject);
        this.root = compiler.root();
        this.config = config != null ? config : GenerationConfig.defaultConfig();
        this.entropy = entropy;
    }

    /** 从 JSON 字符串构建生成器。 */
    public static JsonGenerator of(String schemaJson) {
        return of(JsonParser.parse(schemaJson), GenerationConfig.defaultConfig(), null);
    }

    /** 从 JSON 字符串构建生成器，注入熵源（同一种子可复现）。 */
    public static JsonGenerator of(String schemaJson, RandomGenerator entropy) {
        return of(JsonParser.parse(schemaJson), GenerationConfig.defaultConfig(), entropy);
    }

    /** 从解析后的 Java 对象构建生成器。 */
    public static JsonGenerator of(Object schemaObject) {
        return of(schemaObject, GenerationConfig.defaultConfig(), null);
    }

    /** 从解析后的 Java 对象构建生成器，注入熵源（同一种子可复现）。 */
    public static JsonGenerator of(Object schemaObject, RandomGenerator entropy) {
        return of(schemaObject, GenerationConfig.defaultConfig(), entropy);
    }

    /** 从解析后的 Java 对象构建生成器，自定义配置。 */
    public static JsonGenerator of(Object schemaObject, GenerationConfig config) {
        return of(schemaObject, config, null);
    }

    /** 从解析后的 Java 对象构建生成器，自定义配置与熵源。 */
    public static JsonGenerator of(Object schemaObject, GenerationConfig config, RandomGenerator entropy) {
        return new JsonGenerator(schemaObject, config, entropy);
    }

    /** 生成随机实例（Map/List 嵌套结构）。 */
    public Object generate() {
        RandomGenerator source = entropy != null ? entropy : new Random();
        return root.generate(new GenerationContext(new RandomSupport(source), config));
    }

    /** 生成随机实例并序列化为紧凑 JSON 字符串。 */
    public String generateJson() {
        return JsonBuilder.toJsonString(generate());
    }
}
