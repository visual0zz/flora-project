package com.flora.root.mock.jsonschema;

import com.flora.root.codec.json.model.JsonValue;
import com.flora.root.codec.jsonschema.JsonSchema;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 生成结果回验：对一批覆盖各关键字的 schema，用固定种子反复生成，
 * 断言结果一定通过 {@link JsonSchema} 校验，且不会卡死。
 * <p>这类回验能自动抓住"生成了不合法实例"和"值空间不足导致空转"两类问题。</p>
 */
class JsonGeneratorPropertyTest {

    private static final List<String> SCHEMAS = List.of(
            // 对象：必填 + 数值范围
            "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"},"
                    + "\"age\":{\"type\":\"integer\",\"minimum\":0,\"maximum\":120}},"
                    + "\"required\":[\"name\",\"age\"]}",
            // 数组：元素长度约束与数量约束
            "{\"type\":\"array\",\"items\":{\"type\":\"string\",\"minLength\":2,\"maxLength\":6},"
                    + "\"minItems\":2,\"maxItems\":5}",
            // uniqueItems：值空间小于长度上限时也不应空转
            "{\"type\":\"array\",\"items\":{\"type\":\"boolean\"},\"uniqueItems\":true,"
                    + "\"minItems\":1,\"maxItems\":3}",
            "{\"type\":\"object\",\"properties\":{\"tags\":{\"type\":\"array\","
                    + "\"items\":{\"type\":\"string\"},\"uniqueItems\":true,\"maxItems\":4}},"
                    + "\"required\":[\"tags\"]}",
            // minProperties 与 additionalProperties 的类型必须一致
            "{\"type\":\"object\",\"minProperties\":3,\"additionalProperties\":{\"type\":\"integer\"}}",
            // patternProperties：属性名必须落在该 pattern 内
            "{\"type\":\"object\",\"patternProperties\":{\"^S_\":{\"type\":\"string\"}},"
                    + "\"additionalProperties\":false}",
            // 字符串：pattern / format / 长度
            "{\"type\":\"string\",\"pattern\":\"^[a-z]{3,8}$\"}",
            "{\"type\":\"string\",\"minLength\":5,\"maxLength\":5}",
            "{\"type\":\"string\",\"format\":\"date-time\"}",
            "{\"type\":\"string\",\"format\":\"uuid\"}",
            // 数值：multipleOf 精确对齐
            "{\"type\":\"number\",\"minimum\":0,\"maximum\":10,\"multipleOf\":0.5}",
            "{\"type\":\"integer\",\"minimum\":3,\"maximum\":7,\"multipleOf\":2}",
            // 组合关键字
            "{\"allOf\":[{\"type\":\"string\",\"minLength\":3},{\"type\":\"string\",\"maxLength\":5}]}",
            "{\"anyOf\":[{\"type\":\"string\"},{\"type\":\"integer\"}]}",
            "{\"enum\":[\"a\",\"bb\",\"ccc\"]}",
            "{\"const\":{\"x\":1}}",
            "{\"type\":[\"string\",\"null\"]}",
            // 嵌套与依赖
            "{\"type\":\"object\",\"properties\":{\"addr\":{\"type\":\"object\","
                    + "\"properties\":{\"city\":{\"type\":\"string\"}},\"required\":[\"city\"]}},"
                    + "\"required\":[\"addr\"]}",
            "{\"type\":\"object\",\"dependentRequired\":{\"a\":[\"b\"]},"
                    + "\"properties\":{\"a\":{\"type\":\"boolean\"},\"b\":{\"type\":\"boolean\"}}}",
            // 元组与 contains
            "{\"type\":\"array\",\"prefixItems\":[{\"type\":\"integer\"},{\"type\":\"string\"}],"
                    + "\"items\":{\"type\":\"boolean\"}}",
            "{\"type\":\"array\",\"contains\":{\"type\":\"integer\"},\"minItems\":1}",
            // 递归
            "{\"$defs\":{\"node\":{\"type\":\"object\",\"properties\":{"
                    + "\"value\":{\"type\":\"integer\"},\"child\":{\"$ref\":\"#/$defs/node\"}},"
                    + "\"required\":[\"value\"]}},\"$ref\":\"#/$defs/node\"}"
    );

    private static final int SEEDS = 30;

    @Test
    void generatedInstancesSatisfySchema() {
        for (String schemaJson : SCHEMAS) {
            JsonSchema schema = JsonSchema.of(schemaJson);
            for (int seed = 0; seed < SEEDS; seed++) {
                int currentSeed = seed;
                assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
                    JsonValue value = JsonGenerator.of(schemaJson, new Random(currentSeed)).generate();
                    assertTrue(schema.isValid(value),
                            () -> "生成结果不满足 schema: " + schemaJson + "\n实际: " + value);
                }, "生成超时（疑似空转）: " + schemaJson);
            }
        }
    }

    @Test
    void sameSeedReproducesSameInstance() {
        String schemaJson = "{\"type\":\"object\",\"properties\":{"
                + "\"id\":{\"type\":\"string\",\"format\":\"uuid\"},"
                + "\"n\":{\"type\":\"integer\",\"minimum\":0,\"maximum\":99}},"
                + "\"required\":[\"id\",\"n\"]}";
        String first = JsonGenerator.of(schemaJson, new Random(11)).generateStr();
        String second = JsonGenerator.of(schemaJson, new Random(11)).generateStr();
        assertEquals(first, second, "同种子应得到相同实例");
    }
}
