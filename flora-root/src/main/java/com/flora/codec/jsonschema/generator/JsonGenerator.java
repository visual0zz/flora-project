package com.flora.codec.jsonschema.generator;

import com.flora.codec.json.JsonParser;

import java.util.Random;

/**
 * JSON Schema 数据生成器门面。
 * <p>根据 2020-12 schema 生成随机且（尽力）符合约束的 JSON 实例。
 * 支持：type/enum/const、数值范围/multipleOf、字符串长度/format/pattern、
 * 数组（prefixItems/items/contains/uniqueItems）、对象（properties/required/依赖）、
 * 组合（anyOf/oneOf/if-then-else/allOf 合并）、{@code $ref}/{@code $defs} 递归（深度限制）。</p>
 *
 * <pre>{@code
 * JsonGenerator generator = JsonGenerator.of("{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}}}");
 * Object instance = generator.generate();
 * }</pre>
 */
public final class JsonGenerator {

    private final GeneratorCompiler compiler;
    private final GenerationNode root;
    private final GenerationConfig config;

    private JsonGenerator(Object schemaObject, GenerationConfig config) {
        this.compiler = GeneratorCompiler.of(schemaObject);
        this.root = compiler.root();
        this.config = config != null ? config : GenerationConfig.defaultConfig();
    }

    /** 从解析后的 Java 对象构建生成器。 */
    public static JsonGenerator of(Object schemaObject) {
        return new JsonGenerator(schemaObject, GenerationConfig.defaultConfig());
    }

    /** 从 JSON 字符串构建生成器。 */
    public static JsonGenerator of(String schemaJson) {
        return of(JsonParser.parse(schemaJson), GenerationConfig.defaultConfig());
    }

    /** 从解析后的对象构建生成器，自定义配置。 */
    public static JsonGenerator of(Object schemaObject, GenerationConfig config) {
        return new JsonGenerator(schemaObject, config);
    }

    /** 生成随机实例。 */
    public Object generate() {
        return root.generate(new GenerationContext(new RandomSupport(new Random()), config));
    }

    /** 用种子生成（可复现）。 */
    public Object generate(long seed) {
        return root.generate(new GenerationContext(new RandomSupport(new Random(seed)), config));
    }
}
