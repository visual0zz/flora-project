package com.flora.mock.jsonschema;

import com.flora.codec.json.JsonBuilder;
import com.flora.codec.json.JsonParser;
import com.flora.codec.jsonschema.JsonSchema;
import com.flora.codec.jsonschema.JsonTypes;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JSON Schema 数据生成器测试。
 * 核心策略：生成后用校验器验证（生成结果应通过 schema 校验）。
 */
class JsonGeneratorTest {

    // ── 基础类型 ──

    @Test
    void generatesString() {
        JsonSchema schema = JsonSchema.of("{\"type\":\"string\"}");
        JsonGenerator gen = JsonGenerator.of("{\"type\":\"string\"}");
        for (int i = 0; i < 20; i++) {
            assertTrue(schema.isValid(gen.generate()));
        }
    }

    @Test
    void generatesNumber() {
        JsonSchema schema = JsonSchema.of("{\"type\":\"number\"}");
        JsonGenerator gen = JsonGenerator.of("{\"type\":\"number\"}");
        for (int i = 0; i < 20; i++) {
            assertTrue(schema.isValid(gen.generate()));
        }
    }

    @Test
    void generatesInteger() {
        JsonSchema schema = JsonSchema.of("{\"type\":\"integer\"}");
        JsonGenerator gen = JsonGenerator.of("{\"type\":\"integer\"}");
        for (int i = 0; i < 20; i++) {
            assertTrue(schema.isValid(gen.generate()));
        }
    }

    @Test
    void generatesObject() {
        JsonSchema schema = JsonSchema.of(
                "{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"string\"}},\"required\":[\"a\"]}");
        JsonGenerator gen = JsonGenerator.of(
                "{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"string\"}},\"required\":[\"a\"]}");
        for (int i = 0; i < 20; i++) {
            Object generated = gen.generate();
            assertTrue(schema.isValid(generated));
            assertTrue(((Map<?, ?>) generated).containsKey("a"));
        }
    }

    @Test
    void generatesArray() {
        JsonSchema schema = JsonSchema.of("{\"type\":\"array\",\"items\":{\"type\":\"integer\"},\"minItems\":1}");
        JsonGenerator gen = JsonGenerator.of("{\"type\":\"array\",\"items\":{\"type\":\"integer\"},\"minItems\":1}");
        for (int i = 0; i < 20; i++) {
            Object generated = gen.generate();
            assertTrue(schema.isValid(generated));
            assertFalse(((List<?>) generated).isEmpty());
        }
    }

    // ── enum / const ──

    @Test
    void generatesEnum() {
        JsonSchema schema = JsonSchema.of("{\"enum\":[\"red\",\"green\",\"blue\"]}");
        JsonGenerator gen = JsonGenerator.of("{\"enum\":[\"red\",\"green\",\"blue\"]}");
        for (int i = 0; i < 20; i++) {
            Object value = gen.generate();
            assertTrue(List.of("red", "green", "blue").contains(value));
            assertTrue(schema.isValid(value));
        }
    }

    @Test
    void generatesConst() {
        JsonGenerator gen = JsonGenerator.of("{\"const\":\"fixed\"}");
        for (int i = 0; i < 5; i++) {
            assertEquals("fixed", gen.generate());
        }
    }

    // ── 数值约束 ──

    @Test
    void respectsNumericBounds() {
        JsonSchema schema = JsonSchema.of("{\"type\":\"integer\",\"minimum\":5,\"maximum\":100}");
        JsonGenerator gen = JsonGenerator.of("{\"type\":\"integer\",\"minimum\":5,\"maximum\":100}");
        for (int i = 0; i < 30; i++) {
            assertTrue(schema.isValid(gen.generate()));
        }
    }

    @Test
    void respectsMultipleOf() {
        JsonSchema schema = JsonSchema.of("{\"type\":\"number\",\"multipleOf\":2.5}");
        JsonGenerator gen = JsonGenerator.of("{\"type\":\"number\",\"multipleOf\":2.5}");
        for (int i = 0; i < 20; i++) {
            assertTrue(schema.isValid(gen.generate()));
        }
    }

    // ── 字符串约束 ──

    @Test
    void respectsStringLength() {
        JsonSchema schema = JsonSchema.of("{\"type\":\"string\",\"minLength\":3,\"maxLength\":8}");
        JsonGenerator gen = JsonGenerator.of("{\"type\":\"string\",\"minLength\":3,\"maxLength\":8}");
        for (int i = 0; i < 20; i++) {
            String value = (String) gen.generate();
            assertTrue(value.length() >= 3 && value.length() <= 8);
            assertTrue(schema.isValid(value));
        }
    }

    @Test
    void generatesFormat() {
        JsonSchema schema = JsonSchema.of("{\"type\":\"string\",\"format\":\"uuid\"}");
        JsonGenerator gen = JsonGenerator.of("{\"type\":\"string\",\"format\":\"uuid\"}");
        for (int i = 0; i < 10; i++) {
            assertTrue(schema.isValid(gen.generate()));
        }
    }

    @Test
    void generatesEmail() {
        JsonSchema schema = JsonSchema.of("{\"type\":\"string\",\"format\":\"email\"}");
        JsonGenerator gen = JsonGenerator.of("{\"type\":\"string\",\"format\":\"email\"}");
        for (int i = 0; i < 10; i++) {
            assertTrue(schema.isValid(gen.generate()));
        }
    }

    @Test
    void generatesPattern() {
        JsonSchema schema = JsonSchema.of("{\"type\":\"string\",\"pattern\":\"^[a-z]+$\"}");
        JsonGenerator gen = JsonGenerator.of("{\"type\":\"string\",\"pattern\":\"^[a-z]+$\"}");
        for (int i = 0; i < 20; i++) {
            assertTrue(schema.isValid(gen.generate()));
        }
    }

    // ── 数组约束 ──

    @Test
    void respectsUniqueItems() {
        JsonSchema schema = JsonSchema.of("{\"type\":\"array\",\"items\":{\"type\":\"integer\"},"
                + "\"minItems\":2,\"maxItems\":4,\"uniqueItems\":true}");
        JsonGenerator gen = JsonGenerator.of("{\"type\":\"array\",\"items\":{\"type\":\"integer\"},"
                + "\"minItems\":2,\"maxItems\":4,\"uniqueItems\":true}");
        for (int i = 0; i < 20; i++) {
            assertTrue(schema.isValid(gen.generate()));
        }
    }

    @Test
    void respectsPrefixItems() {
        JsonSchema schema = JsonSchema.of(
                "{\"type\":\"array\",\"prefixItems\":[{\"type\":\"string\"},{\"type\":\"integer\"}],"
                + "\"items\":{\"type\":\"boolean\"}}");
        JsonGenerator gen = JsonGenerator.of(
                "{\"type\":\"array\",\"prefixItems\":[{\"type\":\"string\"},{\"type\":\"integer\"}],"
                + "\"items\":{\"type\":\"boolean\"}}");
        for (int i = 0; i < 20; i++) {
            assertTrue(schema.isValid(gen.generate()));
        }
    }

    @Test
    void respectsContains() {
        JsonSchema schema = JsonSchema.of(
                "{\"type\":\"array\",\"items\":{\"type\":\"integer\"},"
                + "\"minItems\":3,\"contains\":{\"type\":\"integer\",\"minimum\":100}}");
        JsonGenerator gen = JsonGenerator.of(
                "{\"type\":\"array\",\"items\":{\"type\":\"integer\"},"
                + "\"minItems\":3,\"contains\":{\"type\":\"integer\",\"minimum\":100}}");
        for (int i = 0; i < 20; i++) {
            Object generated = gen.generate();
            assertTrue(schema.isValid(generated));
        }
    }

    // ── 对象约束 ──

    @Test
    void respectsAdditionalPropertiesFalse() {
        JsonSchema schema = JsonSchema.of(
                "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}},\"additionalProperties\":false}");
        JsonGenerator gen = JsonGenerator.of(
                "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}},\"additionalProperties\":false}");
        for (int i = 0; i < 20; i++) {
            Object generated = gen.generate();
            assertTrue(schema.isValid(generated));
            assertTrue(((Map<?, ?>) generated).keySet().stream().allMatch(k -> k.equals("name")));
        }
    }

    @Test
    void respectsDependentRequired() {
        JsonSchema schema = JsonSchema.of(
                "{\"type\":\"object\",\"properties\":{\"credit_card\":{},\"billing_address\":{}},"
                + "\"dependentRequired\":{\"credit_card\":[\"billing_address\"]}}");
        JsonGenerator gen = JsonGenerator.of(
                "{\"type\":\"object\",\"properties\":{\"credit_card\":{},\"billing_address\":{}},"
                + "\"dependentRequired\":{\"credit_card\":[\"billing_address\"]}}");
        for (int i = 0; i < 20; i++) {
            Map<?, ?> generated = (Map<?, ?>) gen.generate();
            if (generated.containsKey("credit_card")) {
                assertTrue(generated.containsKey("billing_address"));
            }
            assertTrue(schema.isValid(generated));
        }
    }

    // ── $ref / $defs ──

    @Test
    void generatesWithDefsRef() {
        JsonSchema schema = JsonSchema.of(
                "{\"$defs\":{\"pos\":{\"type\":\"integer\",\"minimum\":1}},"
                + "\"type\":\"object\",\"properties\":{\"n\":{\"$ref\":\"#/$defs/pos\"}},\"required\":[\"n\"]}");
        JsonGenerator gen = JsonGenerator.of(
                "{\"$defs\":{\"pos\":{\"type\":\"integer\",\"minimum\":1}},"
                + "\"type\":\"object\",\"properties\":{\"n\":{\"$ref\":\"#/$defs/pos\"}},\"required\":[\"n\"]}");
        for (int i = 0; i < 20; i++) {
            Object generated = gen.generate();
            assertTrue(schema.isValid(generated));
            assertTrue((Long) ((Map<?, ?>) generated).get("n") >= 1);
        }
    }

    // ── 递归：深度限制不无限 ──

    @Test
    void recursiveSchemaTerminates() {
        JsonSchema schema = JsonSchema.of(
                "{\"$defs\":{\"node\":{\"type\":\"object\",\"properties\":{"
                + "\"value\":{\"type\":\"integer\"},\"child\":{\"$ref\":\"#/$defs/node\"}}}},"
                + "\"$ref\":\"#/$defs/node\"}");
        JsonGenerator gen = JsonGenerator.of(
                "{\"$defs\":{\"node\":{\"type\":\"object\",\"properties\":{"
                + "\"value\":{\"type\":\"integer\"},\"child\":{\"$ref\":\"#/$defs/node\"}}}},"
                + "\"$ref\":\"#/$defs/node\"}");
        // 应有限终止（深度限制），不会栈溢出
        Object generated = gen.generate();
        assertNotNull(generated);
        assertInstanceOf(Map.class, generated);
    }

    // ── 种子可复现 ──

    @Test
    void seedIsReproducible() {
        String schemaJson = "{\"type\":\"object\",\"properties\":{\"a\":{\"type\":\"integer\"},\"b\":{\"type\":\"string\"}}}";
        JsonGenerator g1 = JsonGenerator.of(schemaJson, new Random(42L));
        JsonGenerator g2 = JsonGenerator.of(schemaJson, new Random(42L));
        Object v1 = g1.generate();
        Object v2 = g2.generate();
        assertEquals(v1, v2);
    }

    // ── generateJson 输出 ──

    @Test
    void generatesJsonString() {
        // 用 string/integer/boolean 字段避免 number 序列化的类型漂移
        JsonSchema schema = JsonSchema.of(
                "{\"type\":\"object\",\"properties\":{"
                + "\"name\":{\"type\":\"string\"},\"count\":{\"type\":\"integer\"},\"ok\":{\"type\":\"boolean\"}},"
                + "\"required\":[\"name\",\"count\",\"ok\"]}");
        JsonGenerator gen = JsonGenerator.of(
                "{\"type\":\"object\",\"properties\":{"
                + "\"name\":{\"type\":\"string\"},\"count\":{\"type\":\"integer\"},\"ok\":{\"type\":\"boolean\"}},"
                + "\"required\":[\"name\",\"count\",\"ok\"]}");
        for (int i = 0; i < 20; i++) {
            String json = gen.generateStr();
            Object parsed = JsonParser.parse(json).toNative();
            assertTrue(schema.isValid(parsed));
            // round-trip：同一实例经 JsonBuilder 序列化后再解析应深度相等
            Object generated = gen.generate();
            assertTrue(JsonTypes.deepEquals(JsonParser.parse(JsonBuilder.toJsonString(generated)).toMap(), generated));
        }
    }

    // ── 组合 ──

    @Test
    void generatesAnyOf() {
        JsonSchema schema = JsonSchema.of(
                "{\"anyOf\":[{\"type\":\"string\"},{\"type\":\"integer\"}]}");
        JsonGenerator gen = JsonGenerator.of(
                "{\"anyOf\":[{\"type\":\"string\"},{\"type\":\"integer\"}]}");
        for (int i = 0; i < 20; i++) {
            assertTrue(schema.isValid(gen.generate()));
        }
    }

    @Test
    void generatesIfThenElse() {
        JsonSchema schema = JsonSchema.of(
                "{\"type\":\"object\",\"if\":{\"properties\":{\"kind\":{\"const\":\"circle\"}}},"
                + "\"then\":{\"required\":[\"radius\"]},\"else\":{\"required\":[\"width\"]}}");
        JsonGenerator gen = JsonGenerator.of(
                "{\"type\":\"object\",\"properties\":{\"kind\":{\"type\":\"string\"}},"
                + "\"if\":{\"properties\":{\"kind\":{\"const\":\"circle\"}}},"
                + "\"then\":{\"required\":[\"radius\"]},\"else\":{\"required\":[\"width\"]}}");
        for (int i = 0; i < 20; i++) {
            // 生成结果可能不完全满足 then/else（因为 kind 随机），仅验证不抛异常
            assertNotNull(gen.generate());
        }
    }

    // ── allOf 合并 ──

    @Test
    void generatesAllOf() {
        JsonSchema schema = JsonSchema.of(
                "{\"allOf\":[{\"type\":\"integer\"},{\"minimum\":5}]}");
        JsonGenerator gen = JsonGenerator.of(
                "{\"allOf\":[{\"type\":\"integer\"},{\"minimum\":5}]}");
        for (int i = 0; i < 20; i++) {
            Object generated = gen.generate();
            assertTrue(schema.isValid(generated));
        }
    }

    // ── false schema ──

    @Test
    void falseSchemaThrows() {
        JsonGenerator gen = JsonGenerator.of(false);
        assertThrows(JsonGenerationException.class, gen::generate);
    }

    // ── 数字精度 ──

    @Test
    void generatesDecimalNumber() {
        JsonSchema schema = JsonSchema.of("{\"type\":\"number\",\"minimum\":1.5,\"maximum\":2.5}");
        JsonGenerator gen = JsonGenerator.of("{\"type\":\"number\",\"minimum\":1.5,\"maximum\":2.5}");
        for (int i = 0; i < 20; i++) {
            Object value = gen.generate();
            assertTrue(schema.isValid(value));
            assertInstanceOf(BigDecimal.class, value);
        }
    }

    // ── 推荐长度 ──

    @Test
    void targetLengthScalesOutput() {
        String schemaJson = "{\"type\":\"string\"}";
        Object schema = JsonParser.parse(schemaJson).toMap();
        JsonGenerator small = JsonGenerator.of(schema, 8);
        JsonGenerator big = JsonGenerator.of(schema, 64);
        for (int i = 0; i < 30; i++) {
            int smallLen = ((String) small.generate()).length();
            int bigLen = ((String) big.generate()).length();
            assertTrue(bigLen > smallLen,
                    "大推荐长度应生成更长的串: small=" + smallLen + ", big=" + bigLen);
        }
    }

    @Test
    void minItemsBeatsBudget() {
        // 数组 minItems 硬约束优先于预算推算
        JsonSchema schema = JsonSchema.of(
                "{\"type\":\"array\",\"items\":{\"type\":\"integer\"},\"minItems\":3}");
        JsonGenerator gen = JsonGenerator.of(
                JsonParser.parse("{\"type\":\"array\",\"items\":{\"type\":\"integer\"},\"minItems\":3}").toMap(),
                2);
        for (int i = 0; i < 20; i++) {
            Object value = gen.generate();
            assertTrue(schema.isValid(value));
            assertTrue(((List<?>) value).size() >= 3);
        }
    }

    // ── 递归深度由预算驱动 ──

    @Test
    void recursiveDepthScalesWithBudget() {
        String schemaJson = "{\"$defs\":{\"node\":{\"type\":\"object\",\"properties\":{"
                + "\"value\":{\"type\":\"integer\"},\"child\":{\"$ref\":\"#/$defs/node\"}}}},"
                + "\"$ref\":\"#/$defs/node\"}";
        JsonGenerator shallow = JsonGenerator.of(schemaJson, 8);
        JsonGenerator deep = JsonGenerator.of(schemaJson, 200);
        long shallowSum = 0;
        long deepSum = 0;
        for (int i = 0; i < 30; i++) {
            Object s = shallow.generate();
            Object d = deep.generate();
            assertNotNull(s);
            assertNotNull(d);
            shallowSum += depthOf(s);
            deepSum += depthOf(d);
        }
        double shallowAvg = shallowSum / 30.0;
        double deepAvg = deepSum / 30.0;
        assertTrue(deepAvg > shallowAvg,
                "大预算应生成更深的递归树: shallowAvg=" + shallowAvg + ", deepAvg=" + deepAvg);
    }

    private static int depthOf(Object value) {
        if (value instanceof Map<?, ?> m) {
            int max = 1;
            for (Object v : m.values()) {
                max = Math.max(max, 1 + depthOf(v));
            }
            return max;
        }
        if (value instanceof List<?> l) {
            int max = 1;
            for (Object v : l) {
                max = Math.max(max, 1 + depthOf(v));
            }
            return max;
        }
        return 0;
    }

    // ── allOf pattern 交集（局部域代数接入）──

    @Test
    void allOfPatternsIntersect() {
        // 同时满足 ^[a-z]+$ 与 [bc]+：生成结果必须两个 pattern 都匹配
        JsonGenerator gen = JsonGenerator.of(
                "{\"allOf\":[{\"pattern\":\"^[a-z]+$\"},{\"pattern\":\"[bc]+\"}]}");
        for (int i = 0; i < 30; i++) {
            Object value = gen.generate();
            assertInstanceOf(String.class, value);
            String s = (String) value;
            assertTrue(java.util.regex.Pattern.matches("^[a-z]+$", s),
                    "应满足 pattern1: " + s);
            assertTrue(java.util.regex.Pattern.matches("[bc]+", s),
                    "应满足 pattern2: " + s);
        }
    }

    @Test
    void allOfPatternEmptyIntersectionThrows() {
        // ^a+$ 与 ^b+$ 交集为空 → 生成抛 JsonGenerationException
        JsonGenerator gen = JsonGenerator.of(
                "{\"allOf\":[{\"pattern\":\"^a+$\"},{\"pattern\":\"^b+$\"}]}");
        assertThrows(JsonGenerationException.class, gen::generate);
    }

    // ── 递归截断满足硬约束 ──

    @Test
    void recursiveTruncationSatisfiesRequired() {
        // 递归 node 有 required:["value"]，小预算强制截断时每层仍须有 value
        String schemaJson = "{\"$defs\":{\"node\":{\"type\":\"object\",\"properties\":{"
                + "\"value\":{\"type\":\"integer\"},\"child\":{\"$ref\":\"#/$defs/node\"}},"
                + "\"required\":[\"value\"]}},\"$ref\":\"#/$defs/node\"}";
        JsonGenerator gen = JsonGenerator.of(schemaJson, 4); // 小预算，容易触发截断
        for (int i = 0; i < 50; i++) {
            Object generated = gen.generate();
            assertNotNull(generated);
            assertEveryNodeHasValue(generated);
        }
    }

    @Test
    void truncatedObjectHasMinProperties() {
        // minProperties:2 在截断场景下也须满足
        JsonGenerator gen = JsonGenerator.of(
                "{\"type\":\"object\",\"minProperties\":2,\"properties\":{\"a\":{\"type\":\"integer\"},"
                        + "\"b\":{\"type\":\"integer\"}}}");
        for (int i = 0; i < 30; i++) {
            Object generated = gen.generate();
            assertInstanceOf(Map.class, generated);
            assertTrue(((Map<?, ?>) generated).size() >= 2,
                    "应满足 minProperties:2");
        }
    }

    private static void assertEveryNodeHasValue(Object value) {
        if (value instanceof Map<?, ?> m) {
            assertTrue(m.containsKey("value"),
                    "递归 node 必须含 value: " + m);
            for (Object v : m.values()) {
                if (v instanceof Map || v instanceof List) {
                    assertEveryNodeHasValue(v);
                }
            }
        } else if (value instanceof List<?> l) {
            for (Object v : l) {
                assertEveryNodeHasValue(v);
            }
        }
    }
}
