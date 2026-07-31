package com.flora.codec.jsonschema;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JSON Schema 2020-12 校验器测试：基础关键字、容器、组合、引用、求值、format。
 */
class JsonSchemaTest {

    // ── type / enum / const ──

    @Test
    void typeValidation() {
        JsonSchema schema = JsonSchema.of("{\"type\":\"object\"}");
        assertTrue(schema.isValid(java.util.Map.of("a", 1)));
        assertFalse(schema.isValid(java.util.List.of(1)));
    }

    @Test
    void typeUnion() {
        JsonSchema schema = JsonSchema.of("{\"type\":[\"string\",\"null\"]}");
        assertTrue(schema.isValid("hi"));
        assertTrue(schema.isValid(null));
        assertFalse(schema.isValid(42));
    }

    @Test
    void integerMatchesWholeNumbers() {
        JsonSchema schema = JsonSchema.of("{\"type\":\"integer\"}");
        assertTrue(schema.isValid(42L));
        assertTrue(schema.isValid(42.0));
        assertFalse(schema.isValid(42.5));
    }

    @Test
    void enumValidation() {
        JsonSchema schema = JsonSchema.of("{\"enum\":[\"red\",\"green\",42]}");
        assertTrue(schema.isValid("red"));
        assertTrue(schema.isValid(42));
        assertFalse(schema.isValid("blue"));
    }

    @Test
    void constValidation() {
        JsonSchema schema = JsonSchema.of("{\"const\":\"fixed\"}");
        assertTrue(schema.isValid("fixed"));
        assertFalse(schema.isValid("other"));
    }

    // ── 数值 ──

    @Test
    void numericBounds() {
        JsonSchema schema = JsonSchema.of(
                "{\"minimum\":1,\"maximum\":10,\"exclusiveMinimum\":0,\"exclusiveMaximum\":100}");
        assertTrue(schema.isValid(5));
        assertTrue(schema.isValid(1));
        assertFalse(schema.isValid(0));
        assertFalse(schema.isValid(11));
    }

    @Test
    void multipleOf() {
        JsonSchema schema = JsonSchema.of("{\"multipleOf\":2.5}");
        assertTrue(schema.isValid(5));
        assertTrue(schema.isValid(7.5));
        assertFalse(schema.isValid(6));
    }

    @Test
    void numericKeywordsSkipNonNumbers() {
        JsonSchema schema = JsonSchema.of("{\"minimum\":5}");
        assertTrue(schema.isValid("abc")); // 非数字跳过
        assertFalse(schema.isValid(3));
    }

    // ── 字符串 ──

    @Test
    void stringLength() {
        JsonSchema schema = JsonSchema.of("{\"minLength\":2,\"maxLength\":4}");
        assertTrue(schema.isValid("abc"));
        assertFalse(schema.isValid("a"));
        assertFalse(schema.isValid("abcde"));
    }

    @Test
    void pattern() {
        JsonSchema schema = JsonSchema.of("{\"pattern\":\"^[a-z]+$\"}");
        assertTrue(schema.isValid("abc"));
        assertFalse(schema.isValid("ABC"));
    }

    // ── 数组 ──

    @Test
    void arrayLengthAndUnique() {
        JsonSchema schema = JsonSchema.of("{\"minItems\":1,\"maxItems\":3,\"uniqueItems\":true}");
        assertTrue(schema.isValid(List.of(1, 2)));
        assertFalse(schema.isValid(List.of()));
        assertFalse(schema.isValid(List.of(1, 1)));
        assertFalse(schema.isValid(List.of(1, 2, 3, 4)));
    }

    @Test
    void prefixItemsAndItems() {
        JsonSchema schema = JsonSchema.of(
                "{\"prefixItems\":[{\"type\":\"string\"},{\"type\":\"integer\"}],"
                + "\"items\":{\"type\":\"boolean\"}}");
        assertTrue(schema.isValid(List.of("a", 1, true, false)));
        assertFalse(schema.isValid(List.of(1)));
        assertFalse(schema.isValid(List.of("a", "b")));
    }

    @Test
    void itemsFalseForbidsExtra() {
        JsonSchema schema = JsonSchema.of("{\"prefixItems\":[{\"type\":\"string\"}],\"items\":false}");
        assertTrue(schema.isValid(List.of("a")));
        assertFalse(schema.isValid(List.of("a", "b")));
    }

    @Test
    void contains() {
        JsonSchema schema = JsonSchema.of("{\"contains\":{\"type\":\"string\"}}");
        assertTrue(schema.isValid(List.of(1, "a", 2)));
        assertFalse(schema.isValid(List.of(1, 2, 3)));
    }

    // ── 对象 ──

    @Test
    void propertiesAndRequired() {
        JsonSchema schema = JsonSchema.of(
                "{\"properties\":{\"name\":{\"type\":\"string\"},\"age\":{\"type\":\"integer\"}},"
                + "\"required\":[\"name\"]}");
        assertTrue(schema.isValid(java.util.Map.of("name", "x", "age", 30)));
        assertFalse(schema.isValid(java.util.Map.of("age", 30)));
        assertFalse(schema.isValid(java.util.Map.of("name", 123)));
    }

    @Test
    void additionalPropertiesFalse() {
        JsonSchema schema = JsonSchema.of(
                "{\"properties\":{\"name\":{}},\"additionalProperties\":false}");
        assertTrue(schema.isValid(java.util.Map.of("name", "x")));
        assertFalse(schema.isValid(java.util.Map.of("name", "x", "extra", 1)));
    }

    @Test
    void dependentRequired() {
        JsonSchema schema = JsonSchema.of(
                "{\"dependentRequired\":{\"credit_card\":[\"billing_address\"]}}");
        assertTrue(schema.isValid(java.util.Map.of("credit_card", 1, "billing_address", "addr")));
        assertFalse(schema.isValid(java.util.Map.of("credit_card", 1)));
        assertTrue(schema.isValid(java.util.Map.of("other", 1)));
    }

    @Test
    void propertyNames() {
        JsonSchema schema = JsonSchema.of("{\"propertyNames\":{\"pattern\":\"^[a-z]+$\"}}");
        assertTrue(schema.isValid(java.util.Map.of("abc", 1)));
        assertFalse(schema.isValid(java.util.Map.of("ABC", 1)));
    }

    // ── 组合 ──

    @Test
    void allOf() {
        JsonSchema schema = JsonSchema.of(
                "{\"allOf\":[{\"type\":\"integer\"},{\"minimum\":5}]}");
        assertTrue(schema.isValid(7));
        assertFalse(schema.isValid(3));
        assertFalse(schema.isValid("x"));
    }

    @Test
    void anyOf() {
        JsonSchema schema = JsonSchema.of(
                "{\"anyOf\":[{\"type\":\"string\"},{\"type\":\"integer\"}]}");
        assertTrue(schema.isValid("x"));
        assertTrue(schema.isValid(1));
        assertFalse(schema.isValid(1.5));
    }

    @Test
    void oneOf() {
        JsonSchema schema = JsonSchema.of(
                "{\"oneOf\":[{\"type\":\"integer\"},{\"minimum\":2}]}");
        assertTrue(schema.isValid(1));   // 仅 integer 分支通过
        assertFalse(schema.isValid(3));  // 两个分支都通过
        assertTrue(schema.isValid("x")); // minimum 对非数字无约束，仅此分支通过
    }

    @Test
    void not() {
        JsonSchema schema = JsonSchema.of("{\"not\":{\"type\":\"string\"}}");
        assertTrue(schema.isValid(42));
        assertFalse(schema.isValid("x"));
    }

    @Test
    void ifThenElse() {
        JsonSchema schema = JsonSchema.of(
                "{\"if\":{\"properties\":{\"kind\":{\"const\":\"circle\"}}},\"required\":[\"kind\"],"
                + "\"then\":{\"required\":[\"radius\"]},"
                + "\"else\":{\"required\":[\"width\"]}}");
        assertTrue(schema.isValid(java.util.Map.of("kind", "circle", "radius", 5)));
        assertFalse(schema.isValid(java.util.Map.of("kind", "circle")));
        assertTrue(schema.isValid(java.util.Map.of("kind", "rect", "width", 3)));
        assertFalse(schema.isValid(java.util.Map.of("kind", "rect")));
    }

    // ── $ref / $defs ──

    @Test
    void refToDefs() {
        JsonSchema schema = JsonSchema.of(
                "{\"$defs\":{\"positive\":{\"type\":\"integer\",\"minimum\":1}},"
                + "\"$ref\":\"#/$defs/positive\"}");
        assertTrue(schema.isValid(5));
        assertFalse(schema.isValid(0));
        assertFalse(schema.isValid("x"));
    }

    @Test
    void recursiveRef() {
        JsonSchema schema = JsonSchema.of(
                "{\"$defs\":{\"node\":{\"properties\":{\"value\":{\"type\":\"integer\"},"
                + "\"child\":{\"$ref\":\"#/$defs/node\"}}}},"
                + "\"$ref\":\"#/$defs/node\"}");
        assertTrue(schema.isValid(java.util.Map.of("value", 1,
                "child", java.util.Map.of("value", 2,
                        "child", java.util.Map.of("value", 3)))));
        assertFalse(schema.isValid(java.util.Map.of("value", "x")));
    }

    @Test
    void anchorRef() {
        JsonSchema schema = JsonSchema.of(
                "{\"$defs\":{\"p\":{\"$anchor\":\"pos\",\"type\":\"integer\",\"minimum\":1}},"
                + "\"$ref\":\"#pos\"}");
        assertTrue(schema.isValid(5));
        assertFalse(schema.isValid(0));
    }

    // ── unevaluatedProperties / unevaluatedItems ──

    @Test
    void unevaluatedProperties() {
        JsonSchema schema = JsonSchema.of(
                "{\"properties\":{\"a\":{}},\"unevaluatedProperties\":false}");
        assertTrue(schema.isValid(java.util.Map.of("a", 1)));
        assertFalse(schema.isValid(java.util.Map.of("b", 1)));
    }

    @Test
    void unevaluatedPropertiesWithAllOf() {
        JsonSchema schema = JsonSchema.of(
                "{\"allOf\":[{\"properties\":{\"a\":{}}}],\"unevaluatedProperties\":false}");
        assertTrue(schema.isValid(java.util.Map.of("a", 1)));
        assertFalse(schema.isValid(java.util.Map.of("b", 1)));
    }

    @Test
    void unevaluatedItems() {
        JsonSchema schema = JsonSchema.of(
                "{\"prefixItems\":[{\"type\":\"string\"}],\"unevaluatedItems\":false}");
        assertTrue(schema.isValid(List.of("a")));
        assertFalse(schema.isValid(List.of("a", "b")));
    }

    // ── format ──

    @Test
    void formatValidation() {
        JsonSchema schema = JsonSchema.of("{\"type\":\"string\",\"format\":\"email\"}");
        assertTrue(schema.isValid("user@example.com"));
        assertFalse(schema.isValid("not-an-email"));
    }

    @Test
    void formatIpv4() {
        JsonSchema schema = JsonSchema.of("{\"type\":\"string\",\"format\":\"ipv4\"}");
        assertTrue(schema.isValid("192.168.1.1"));
        assertFalse(schema.isValid("999.1.1.1"));
    }

    @Test
    void formatDateTime() {
        JsonSchema schema = JsonSchema.of("{\"type\":\"string\",\"format\":\"date-time\"}");
        assertTrue(schema.isValid("2024-01-15T10:30:00Z"));
        assertFalse(schema.isValid("2024-13-15T10:30:00Z"));
    }

    @Test
    void unknownFormatThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> JsonSchema.of("{\"format\":\"not-a-real-format\"}"));
    }

    // ── 错误报告 ──

    @Test
    void errorPaths() {
        JsonSchema schema = JsonSchema.of(
                "{\"properties\":{\"users\":{\"type\":\"array\",\"items\":"
                + "{\"properties\":{\"name\":{\"type\":\"string\"}},\"required\":[\"name\"]}}}}");
        var instance = java.util.Map.of("users", List.of(java.util.Map.of()));
        List<ValidationError> errors = schema.errors(instance);
        assertFalse(errors.isEmpty());
        ValidationError first = errors.get(0);
        assertEquals("/users/0", first.instancePath());
        assertEquals("required", first.keyword());
    }

    @Test
    void booleanSchema() {
        assertTrue(JsonSchema.of(true).isValid(anything()));
        assertFalse(JsonSchema.of(false).isValid(anything()));
    }

    private static Object anything() {
        return java.util.Map.of("x", 1);
    }
}
