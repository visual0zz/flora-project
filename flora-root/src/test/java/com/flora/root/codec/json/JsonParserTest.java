package com.flora.root.codec.json;

import com.flora.root.codec.json.model.JsonArray;
import com.flora.root.codec.json.model.JsonObject;
import com.flora.root.codec.json.model.JsonValue;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JsonParser 解析器的独立单元测试，基于 JsonValue 模型。
 * 覆盖标准 JSON 解析、边界情况、异常路径。
 */
class JsonParserTest {

    @Test
    void parseObject() {
        JsonObject o = JsonParser.parseObject("{\"a\":1, \"b\":\"x\"}");
        assertEquals(Long.valueOf(1), o.getLong("a"));
        assertEquals("x", o.getString("b"));
    }

    @Test
    void parseArray() {
        JsonArray arr = JsonParser.parseArray("[1, 2, 3]");
        assertEquals(3, arr.size());
        assertEquals(1L, arr.get(0).asNumber().longValue());
    }

    @Test
    void parseNested() {
        JsonValue v = JsonParser.parse("{\"a\":{\"b\":[1,2]}}");
        assertInstanceOf(JsonObject.class, v);
        JsonObject inner = v.asObject().getObject("a");
        assertEquals(2, inner.getArray("b").size());
    }

    @Test
    void parseStringEscapes() {
        JsonObject o = JsonParser.parseObject("{\"s\":\"a\\nb\\tc\\\"d\\\\e/\"}");
        assertEquals("a\nb\tc\"d\\e/", o.getString("s"));
    }

    @Test
    void parseUnicodeEscape() {
        JsonObject o = JsonParser.parseObject("{\"u\":\"\\u0041\"}");
        assertEquals("A", o.getString("u"));
    }

    @Test
    void parseSurrogatePair() {
        JsonObject o = JsonParser.parseObject("{\"g\":\"\\uD834\\uDD1E\"}");
        assertEquals("\uD834\uDD1E", o.getString("g"));
    }

    @Test
    void parseNumbers() {
        JsonObject o = JsonParser.parseObject("{\"i\":42, \"f\":3.14, \"e\":1e5, \"n\":-1}");
        assertEquals(Long.valueOf(42), o.getLong("i"));
        assertEquals(new BigDecimal("3.14"), o.getNumber("f").decimalValue());
        assertEquals(new BigDecimal("1e5"), o.getNumber("e").decimalValue());
        assertEquals(Long.valueOf(-1), o.getLong("n"));
    }

    @Test
    void parseBooleanAndNull() {
        JsonObject o = JsonParser.parseObject("{\"t\":true,\"f\":false,\"n\":null}");
        assertEquals(Boolean.TRUE, o.getBool("t"));
        assertEquals(Boolean.FALSE, o.getBool("f"));
        assertTrue(o.get("n").isNull());
    }

    @Test
    void parseEmptyObject() {
        assertTrue(JsonParser.parseObject("{}").isEmpty());
    }

    @Test
    void parseEmptyArray() {
        assertTrue(JsonParser.parseArray("[]").isEmpty());
    }

    @Test
    void parseWhitespace() {
        JsonObject o = JsonParser.parseObject("  {  \"k\"  :  1  }  ");
        assertEquals(Long.valueOf(1), o.getLong("k"));
    }

    @Test
    void parseBom() {
        JsonObject o = JsonParser.parseObject("\uFEFF{\"a\":1}");
        assertEquals(Long.valueOf(1), o.getLong("a"));
    }

    @Test
    void parseBigInteger() {
        JsonObject o = JsonParser.parseObject("{\"big\":123456789012345678901234567890}");
        assertInstanceOf(BigInteger.class, o.getNumber("big").value());
    }

    // ====== 异常路径 ======

    @Test
    void parseNullThrows() {
        assertThrows(NullPointerException.class, () -> JsonParser.parse(null));
    }

    @Test
    void parseBlankThrows() {
        assertThrows(IllegalStateException.class, () -> JsonParser.parse(""));
        assertThrows(IllegalStateException.class, () -> JsonParser.parse("  "));
    }

    @Test
    void parseTrailingGarbageThrows() {
        assertThrows(IllegalStateException.class, () -> JsonParser.parse("{\"a\":1} x"));
    }

    @Test
    void parseInvalidJsonThrows() {
        assertThrows(IllegalStateException.class, () -> JsonParser.parse("{invalid}"));
        assertThrows(IllegalStateException.class, () -> JsonParser.parse("[1,2,]"));
        assertThrows(IllegalStateException.class, () -> JsonParser.parse("{1:2}"));
    }

    @Test
    void parseObjectNotObjectThrows() {
        assertThrows(IllegalStateException.class, () -> JsonParser.parseObject("[1,2]"));
    }

    @Test
    void parseArrayNotArrayThrows() {
        assertThrows(IllegalStateException.class, () -> JsonParser.parseArray("{\"a\":1}"));
    }

    @Test
    void deeplyNestedArray() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 500; i++) sb.append('[');
        sb.append('1');
        for (int i = 0; i < 500; i++) sb.append(']');
        sb.append(']');
        JsonValue v = JsonParser.parse(sb.toString());
        assertNotNull(v);
        assertTrue(v.isArray());
    }
}
