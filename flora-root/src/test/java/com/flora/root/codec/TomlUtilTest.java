package com.flora.root.codec;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TomlUtil 解析与序列化的单元测试（覆盖 TOML v1.0 核心特性）。
 */
class TomlUtilTest {

    @Test
    void basicKeyValue() {
        String t = "name = \"flora\"\nage = 30\nactive = true\nratio = 0.5";
        Map<String, Object> m = TomlUtil.parse(t);
        assertEquals("flora", m.get("name"));
        assertEquals(Long.valueOf(30), m.get("age"));
        assertEquals(Boolean.TRUE, m.get("active"));
        assertEquals(new BigDecimal("0.5"), m.get("ratio"));
    }

    @Test
    void commentsIgnored() {
        Map<String, Object> m = TomlUtil.parse("# top comment\nkey = \"val\" # inline\n");
        assertEquals("val", m.get("key"));
    }

    @Test
    void literalString() {
        Map<String, Object> m = TomlUtil.parse("path = 'C:\\Windows\\System32'");
        assertEquals("C:\\Windows\\System32", m.get("path"));
    }

    @Test
    void quotedKeys() {
        String t = "\"key.with.dots\" = 1\n'quoted-key' = 2";
        Map<String, Object> m = TomlUtil.parse(t);
        assertEquals(Long.valueOf(1), m.get("key.with.dots"));
        assertEquals(Long.valueOf(2), m.get("quoted-key"));
    }

    @Test
    void dottedKeys() {
        Map<String, Object> m = TomlUtil.parse("a.b.c = 1\nx.y = \"hello\"");
        @SuppressWarnings("unchecked")
        Map<String, Object> a = (Map<String, Object>) m.get("a");
        @SuppressWarnings("unchecked")
        Map<String, Object> ab = (Map<String, Object>) a.get("b");
        assertEquals(Long.valueOf(1), ab.get("c"));
        @SuppressWarnings("unchecked")
        Map<String, Object> x = (Map<String, Object>) m.get("x");
        assertEquals("hello", x.get("y"));
    }

    @Test
    void tables() {
        String t = "[server]\nhost = \"localhost\"\nport = 8080\n\n[server.database]\nurl = \"jdbc\"";
        Map<String, Object> m = TomlUtil.parse(t);
        @SuppressWarnings("unchecked")
        Map<String, Object> srv = (Map<String, Object>) m.get("server");
        assertEquals("localhost", srv.get("host"));
        assertEquals(Long.valueOf(8080), srv.get("port"));
        @SuppressWarnings("unchecked")
        Map<String, Object> db = (Map<String, Object>) srv.get("database");
        assertEquals("jdbc", db.get("url"));
    }

    @Test
    void arrayOfTables() {
        String t = "[[products]]\nname = \"Hammer\"\nsku = 738594937\n\n[[products]]\nname = \"Nail\"\nsku = 284758393";
        Map<String, Object> m = TomlUtil.parse(t);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> prods = (List<Map<String, Object>>) m.get("products");
        assertEquals(2, prods.size());
        assertEquals("Hammer", prods.get(0).get("name"));
        assertEquals(Long.valueOf(284758393), prods.get(1).get("sku"));
    }

    @Test
    void integerBases() {
        String t = "hex = 0xDEAD\nbin = 0b1101\noct = 0o777\ndec = +99\nneg = -17";
        Map<String, Object> m = TomlUtil.parse(t);
        assertEquals(Long.valueOf(0xDEAD), m.get("hex"));
        assertEquals(Long.valueOf(13), m.get("bin"));
        assertEquals(Long.valueOf(511), m.get("oct"));
        assertEquals(Long.valueOf(99), m.get("dec"));
        assertEquals(Long.valueOf(-17), m.get("neg"));
    }

    @Test
    void integerUnderscores() {
        Map<String, Object> m = TomlUtil.parse("val = 1_000_000");
        assertEquals(Long.valueOf(1000000), m.get("val"));
    }

    @Test
    void floatValues() {
        String t = "pi = 3.14\nbig = 6.626e-34\npos = +1.0\nneg = -2.5";
        Map<String, Object> m = TomlUtil.parse(t);
        assertEquals(new BigDecimal("3.14"), m.get("pi"));
        assertEquals(new BigDecimal("6.626e-34"), m.get("big"));
        assertEquals(new BigDecimal("1.0"), m.get("pos"));
        assertEquals(new BigDecimal("-2.5"), m.get("neg"));
    }

    @Test
    void specialFloatValues() {
        Map<String, Object> m = TomlUtil.parse("pinf = inf\nninf = -inf\nnot = nan");
        assertEquals(Double.POSITIVE_INFINITY, m.get("pinf"));
        assertEquals(Double.NEGATIVE_INFINITY, m.get("ninf"));
        assertEquals(Double.NaN, m.get("not"));
    }

    @Test
    void dateTimeValues() {
        String t = "odt = 1979-05-27T07:32:00Z\nldt = 1979-05-27T07:32:00\nld = 1979-05-27\nlt = 07:32:00";
        Map<String, Object> m = TomlUtil.parse(t);
        assertEquals("1979-05-27T07:32:00Z", m.get("odt"));
        assertEquals("1979-05-27T07:32:00", m.get("ldt"));
        assertEquals("1979-05-27", m.get("ld"));
        assertEquals("07:32:00", m.get("lt"));
    }

    @Test
    void arrays() {
        String t = "arr1 = [1, 2, 3]\narr2 = []\narr3 = [\"a\", \"b\"]";
        Map<String, Object> m = TomlUtil.parse(t);
        assertEquals(List.of(1L, 2L, 3L), m.get("arr1"));
        assertEquals(List.of(), m.get("arr2"));
        assertEquals(List.of("a", "b"), m.get("arr3"));
    }

    @Test
    void multiLineArray() {
        String t = "arr = [\n  1,\n  2,\n  3,\n]\n";
        Map<String, Object> m = TomlUtil.parse(t);
        assertEquals(List.of(1L, 2L, 3L), m.get("arr"));
    }

    @Test
    void inlineTables() {
        String t = "point = {x = 1, y = 2}\nemt = {}";
        Map<String, Object> m = TomlUtil.parse(t);
        @SuppressWarnings("unchecked")
        Map<String, Object> pt = (Map<String, Object>) m.get("point");
        assertEquals(Long.valueOf(1), pt.get("x"));
        assertEquals(Long.valueOf(2), pt.get("y"));
        @SuppressWarnings("unchecked")
        Map<String, Object> emt = (Map<String, Object>) m.get("emt");
        assertTrue(emt.isEmpty());
    }

    @Test
    void nestedInlineTables() {
        String t = "cfg = {server = {host = \"local\", port = 8080}}";
        Map<String, Object> m = TomlUtil.parse(t);
        @SuppressWarnings("unchecked")
        Map<String, Object> cfg = (Map<String, Object>) m.get("cfg");
        @SuppressWarnings("unchecked")
        Map<String, Object> srv = (Map<String, Object>) cfg.get("server");
        assertEquals("local", srv.get("host"));
        assertEquals(Long.valueOf(8080), srv.get("port"));
    }

    @Test
    void multiLineBasicString() {
        String t = "str = \"\"\"\nline1\nline2\"\"\"";
        Map<String, Object> m = TomlUtil.parse(t);
        assertEquals("line1\nline2", m.get("str"));
    }

    @Test
    void multiLineLiteralString() {
        String t = "str = '''\nline1\nline2'''";
        Map<String, Object> m = TomlUtil.parse(t);
        assertEquals("line1\nline2", m.get("str"));
    }

    @Test
    void stringEscapes() {
        Map<String, Object> m = TomlUtil.parse("s = \"tab\\there\\nnewline\"");
        assertEquals("tab\there\nnewline", m.get("s"));
    }

    @Test
    void duplicateKeyThrows() {
        assertThrows(IllegalStateException.class,
                () -> TomlUtil.parse("k = 1\nk = 2"));
    }

    @Test
    void invalidSyntaxThrows() {
        assertThrows(IllegalStateException.class,
                () -> TomlUtil.parse("no eq sign"));
    }

    @Test
    void bigIntegerValue() {
        Map<String, Object> m = TomlUtil.parse("big = 123456789012345678901234567890");
        assertTrue(m.get("big") instanceof BigInteger);
    }

    @Test
    void roundTrip() {
        Map<String, Object> original = new java.util.LinkedHashMap<>();
        original.put("name", "flora");
        original.put("nested", Map.of("a", 1L, "b", List.of("x", "y")));
        // 表数组
        original.put("items", List.of(
                Map.of("id", 1L, "val", "first"),
                Map.of("id", 2L, "val", "second")
        ));

        String toml = TomlUtil.toTomlString(original);
        Map<String, Object> back = TomlUtil.parse(toml);

        assertEquals("flora", back.get("name"));
        @SuppressWarnings("unchecked")
        Map<String, Object> n = (Map<String, Object>) back.get("nested");
        assertEquals(Long.valueOf(1), n.get("a"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) back.get("items");
        assertEquals(2, items.size());
        assertEquals("first", items.get(0).get("val"));
        assertEquals("second", items.get(1).get("val"));
    }
}
