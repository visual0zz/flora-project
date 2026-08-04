package com.flora.codec.json;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JsonParser 解析器的独立单元测试。
 * 覆盖标准 JSON 解析、边界情况、异常路径。
 */
class JsonParserTest {

    @Test
    void parseObject() {
        Map<String, Object> m = JsonParser.parseObject("{\"a\":1, \"b\":\"x\"}");
        assertEquals(Long.valueOf(1), m.get("a"));
        assertEquals("x", m.get("b"));
    }

    @Test
    void parseArray() {
        List<Object> list = JsonParser.parseArray("[1, 2, 3]");
        assertEquals(List.of(1L, 2L, 3L), list);
    }

    @Test
    void parseNested() {
        Object v = JsonParser.parse("{\"a\":{\"b\":[1,2]}}");
        assertInstanceOf(Map.class, v);
    }

    @Test
    void parseStringEscapes() {
        Map<String, Object> m = JsonParser.parseObject("{\"s\":\"a\\nb\\tc\\\"d\\\\e/\"}");
        assertEquals("a\nb\tc\"d\\e/", m.get("s"));
    }

    @Test
    void parseUnicodeEscape() {
        Map<String, Object> m = JsonParser.parseObject("{\"u\":\"\\u0041\"}");
        assertEquals("A", m.get("u"));
    }

    @Test
    void parseSurrogatePair() {
        // U+1D11E (MUSICAL SYMBOL G CLEF) = \uD834\uDD1E
        Map<String, Object> m = JsonParser.parseObject("{\"g\":\"\\uD834\\uDD1E\"}");
        assertEquals("\uD834\uDD1E", m.get("g"));
    }

    @Test
    void parseNumbers() {
        Map<String, Object> m = JsonParser.parseObject("{\"i\":42, \"f\":3.14, \"e\":1e5, \"n\":-1}");
        assertEquals(Long.valueOf(42), m.get("i"));
        assertEquals(new BigDecimal("3.14"), m.get("f"));
        assertEquals(new BigDecimal("1e5"), m.get("e"));
        assertEquals(Long.valueOf(-1), m.get("n"));
    }

    @Test
    void parseBooleanAndNull() {
        Map<String, Object> m = JsonParser.parseObject("{\"t\":true,\"f\":false,\"n\":null}");
        assertEquals(Boolean.TRUE, m.get("t"));
        assertEquals(Boolean.FALSE, m.get("f"));
        assertNull(m.get("n"));
    }

    @Test
    void parseEmptyObject() {
        assertEquals(Map.of(), JsonParser.parseObject("{}"));
    }

    @Test
    void parseEmptyArray() {
        assertEquals(List.of(), JsonParser.parseArray("[]"));
    }

    @Test
    void parseWhitespace() {
        assertEquals(Map.of("k", 1L), JsonParser.parseObject("  {  \"k\"  :  1  }  "));
    }

    @Test
    void parseBom() {
        assertEquals(Map.of("a", 1L), JsonParser.parseObject("\uFEFF{\"a\":1}"));
    }

    @Test
    void parseBigInteger() {
        Map<String, Object> m = JsonParser.parseObject("{\"big\":123456789012345678901234567890}");
        assertInstanceOf(java.math.BigInteger.class, m.get("big"));
    }

    // ====== 异常路径 ======

    @Test
    void parseNullThrows() {
        assertThrows(NullPointerException.class, () -> JsonParser.parse(null)); // 因为传入 null 导致 s.charAt(i) NPE
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
        assertThrows(IllegalStateException.class, () -> JsonParser.parse("[1,2,]")); // 尾部逗号在 JSON 中非法
        assertThrows(IllegalStateException.class, () -> JsonParser.parse("{1:2}")); // key 必须是字符串
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
        Object v = JsonParser.parse(sb.toString());
        assertNotNull(v);
    }
}
