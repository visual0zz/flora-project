package com.flora.root.mock.jsonschema;

import com.flora.root.codec.json.JsonBuilder;
import com.flora.root.codec.json.JsonParser;
import com.flora.root.codec.json.model.JsonValue;
import com.flora.root.codec.json.model.JsonBool;
import com.flora.root.mock.jsonschema.impl.GenerationContext;
import com.flora.root.mock.jsonschema.impl.GenerationNode;
import com.flora.root.mock.jsonschema.impl.GeneratorCompiler;
import com.flora.root.mock.jsonschema.impl.JsonConversionsBridge;
import com.flora.root.mock.jsonschema.impl.RandomSupport;
import com.flora.root.tag.ModuleEntry;
import com.flora.root.tag.ThreadFragile;

import java.util.Random;
import java.util.random.RandomGenerator;

/**
 * JSON Schema 数据生成器门面。
 * <p>根据 2020-12 schema 生成随机且（尽力）符合约束的 JSON 实例。
 * 输入可为 JSON 字符串或已解析的 JSON 对象（{@link JsonValue} 模型）；
 * {@link #generate()} 返回 {@link JsonValue} 模型实例（对象/数组/标量），
 * {@link #generateStr()} 返回 JSON 字符串。熵源通过 {@link #of(Object, RandomGenerator)} 注入，
 * 同一种子生成结果可复现。</p>
 *
 * <p><b>生成策略</b>（不使用长度预算）：
 * <ul>
 *   <li><b>字符串</b>：先按字段名猜测含义（{@code email}/{@code phone}/{@code createdAt} 等，
 *       有 {@code format} 时优先按 format）造一个"像样"的值，校验其是否满足本节点的
 *       {@code pattern} 与 {@code minLength}/{@code maxLength}；不合规则重试，
 *       连续 5 次被拒后放弃语义生成，改为按该节点正则的交集自动机采样。
 *       正则不受支持（或交集为空）时降级为长度区间内的随机串——单个字段的正则
 *       不认识不该让整份数据生成失败。</li>
 *   <li><b>可选部分</b>（非 required 属性、额外属性、递归引用）：按一个随深度指数递减的概率
 *       决定是否继续展开一层，越深越可能收住，因此结构规模不再由预算控制。</li>
 * </ul></p>
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
 * {@code $ref}/{@code $defs} 递归（由随深度递减的展开概率收敛，截断时生成满足
 * required/minItems/minLength 等硬约束的最小实例）。</p>
 *
 * <p><b>不支持的语法</b>（忽略或尽力，不保证严格满足）：{@code not}、
 * {@code dependentSchemas}、{@code propertyNames}、{@code unevaluatedProperties/
 * unevaluatedItems}、{@code minContains/maxContains}（仅生成单个 contains 元素）；
 * 复杂 {@code if/then/else} 条件（随机走分支，不保证 if 前提成立）、
 * {@code pattern} 与 {@code minLength/maxLength} 冲突（以 pattern 结构为准，长度可能越界）、
 * 语义值与 {@code pattern} 冲突（5 次拒绝后回退按正则生成）、
 * 生成器不支持的 {@code pattern}（降级为随机串，该字段可能不满足 pattern）、
 * {@code oneOf} 非互斥分支（可能同时满足多个）、{@code allOf} 中未覆盖的约束组合
 * （取交集近似）、递归截断层（不保证最深层非 required 的可选约束）。</p>
 *
 * <pre>{@code
 * JsonGenerator generator = JsonGenerator.of("{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}}}");
 * Object instance = generator.generate();      // Map/List 嵌套对象
 * String json = generator.generateStr();       // JSON 字符串
 * }</pre>
 */
@ModuleEntry
@ThreadFragile("内部缓存 IdentityHashMap 在惰性编译时写入，共享熵源非线程安全，多线程并发 generate() 需外部同步")
public final class JsonGenerator {

    private final GeneratorCompiler compiler;
    private final GenerationNode root;
    private final RandomGenerator entropy;

    private JsonGenerator(JsonValue schemaObject, RandomGenerator entropy) {
        this.compiler = GeneratorCompiler.of(schemaObject);
        this.root = compiler.root();
        this.entropy = entropy;
    }

    /** 从 JSON 字符串构建生成器。 */
    public static JsonGenerator of(String schemaJson) {
        return of(JsonParser.parse(schemaJson), null);
    }

    /** 从 JSON 字符串构建生成器，注入熵源（同一种子可复现）。 */
    public static JsonGenerator of(String schemaJson, RandomGenerator entropy) {
        return of(JsonParser.parse(schemaJson), entropy);
    }

    /** 从解析后的 JSON 对象构建生成器。 */
    public static JsonGenerator of(JsonValue schemaObject) {
        return of(schemaObject, null);
    }

    /** 从布尔 schema 构建生成器（true→恒真，false→恒假）。 */
    public static JsonGenerator of(boolean schema) {
        return of(new JsonBool(schema));
    }

    /** 从解析后的 JSON 对象构建生成器，注入熵源（同一种子可复现）。 */
    public static JsonGenerator of(JsonValue schemaObject, RandomGenerator entropy) {
        return new JsonGenerator(schemaObject, entropy);
    }

    /** 生成随机实例（{@link JsonValue} 模型：对象/数组/标量）。 */
    public JsonValue generate() {
        RandomGenerator source = entropy != null ? entropy : new Random();
        return JsonConversionsBridge.toValue(
                root.generate(new GenerationContext(new RandomSupport(source))));
    }

    /** 生成随机实例并序列化为紧凑 JSON 字符串。 */
    public String generateStr() {
        return JsonBuilder.toJsonString(generate());
    }
}
