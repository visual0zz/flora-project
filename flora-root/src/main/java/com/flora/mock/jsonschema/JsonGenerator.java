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
 * 支持：type/enum/const、数值范围/multipleOf、字符串长度/format/pattern、
 * 数组（prefixItems/items/contains/uniqueItems）、对象（properties/required/依赖）、
 * 组合（anyOf/oneOf/if-then-else/allOf 合并）、{@code $ref}/{@code $defs} 递归
 * （递归深度由推荐长度预算驱动，非硬性上限）。</p>
 * <p>输入可为 JSON 字符串或已解析的 JSON Object（Map/List 嵌套）；
 * {@link #generate()} 返回 Map/List 嵌套对象，{@link #generateStr()} 返回 JSON 字符串。
 * 熵源通过 {@link #of(Object, RandomGenerator)} 注入，同一种子生成结果可复现。</p>
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
