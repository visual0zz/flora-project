package com.flora.codec;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * YamlUtil 解析与序列化的单元测试（尽量完整 YAML 1.2 核心特性）。
 */
class YamlUtilTest {

    @Test
    void blockMappingAndTypes() {
        String y = "name: flora\nage: 30\nactive: true\nratio: 0.5\nmissing: null\nnote: ~";
        Map<String, Object> m = YamlUtil.parseObject(y);
        assertEquals("flora", m.get("name"));
        assertEquals(Long.valueOf(30), m.get("age"));
        assertEquals(Boolean.TRUE, m.get("active"));
        assertEquals(new BigDecimal("0.5"), m.get("ratio"));
        assertNull(m.get("missing"));
        assertNull(m.get("note"));
    }

    @Test
    void nestedMappingAndSequence() {
        String y = "db:\n  url: jdbc\n  pool:\n    - a\n    - b\n  ports: [8080, 8081]";
        @SuppressWarnings("unchecked")
        Map<String, Object> db = (Map<String, Object>) YamlUtil.parseObject(y).get("db");
        assertEquals("jdbc", db.get("url"));
        @SuppressWarnings("unchecked")
        List<Object> pool = (List<Object>) db.get("pool");
        assertEquals(List.of("a", "b"), pool);
        assertEquals(List.of(8080L, 8081L), db.get("ports"));
    }

    @Test
    void numbersInVariousBases() {
        String y = "hex: 0x1F\nbin: 0b101\noct: 0o17\nneg: -5\nbig: 123456789012345678901234567890";
        Map<String, Object> m = YamlUtil.parseObject(y);
        assertEquals(Long.valueOf(31), m.get("hex"));
        assertEquals(Long.valueOf(5), m.get("bin"));
        assertEquals(Long.valueOf(15), m.get("oct"));
        assertEquals(Long.valueOf(-5), m.get("neg"));
        assertTrue(m.get("big") instanceof BigInteger);
    }

    @Test
    void quotedScalars() {
        String y = "q: \"hello world\"\nsq: 'it''s'\nempty: \"\"";
        Map<String, Object> m = YamlUtil.parseObject(y);
        assertEquals("hello world", m.get("q"));
        assertEquals("it's", m.get("sq"));
        assertEquals("", m.get("empty"));
    }

    @Test
    void blockScalars() {
        Map<String, Object> m = YamlUtil.parseObject("text: |\n  line1\n  line2\n");
        assertEquals("line1\nline2\n", m.get("text"));
        Map<String, Object> f = YamlUtil.parseObject("text: >\n  line1\n  line2\n");
        assertEquals("line1 line2\n", f.get("text"));
    }

    @Test
    void commentsIgnored() {
        Map<String, Object> m = YamlUtil.parseObject("# top\nkey: val # inline\n# tail\n");
        assertEquals("val", m.get("key"));
    }

    @Test
    void anchorsAndAliases() {
        Map<String, Object> m = YamlUtil.parseObject("base: &a\n  x: 1\ncopy: *a");
        @SuppressWarnings("unchecked")
        Map<String, Object> base = (Map<String, Object>) m.get("base");
        assertEquals(Long.valueOf(1), base.get("x"));
        assertEquals(base, m.get("copy"));
    }

    @Test
    void mergeKey() {
        Map<String, Object> m = YamlUtil.parseObject(
                "defaults: &d\n  a: 1\n  b: 2\nitem:\n  <<: *d\n  b: 3\n  c: 4");
        @SuppressWarnings("unchecked")
        Map<String, Object> item = (Map<String, Object>) m.get("item");
        assertEquals(Long.valueOf(1), item.get("a"));
        assertEquals(Long.valueOf(3), item.get("b")); // 被覆盖
        assertEquals(Long.valueOf(4), item.get("c"));
    }

    @Test
    void standardTags() {
        Map<String, Object> m = YamlUtil.parseObject("v: !!str 123\nn: !!int 5\nf: !!float 2.0");
        assertEquals("123", m.get("v"));
        assertEquals(Long.valueOf(5), m.get("n"));
        assertEquals(new BigDecimal("2.0"), m.get("f"));
    }

    @Test
    void flowStyle() {
        Map<String, Object> m = YamlUtil.parseObject("list: [1, 2, 3]\nmap: {a: 1, b: two}");
        assertEquals(List.of(1L, 2L, 3L), m.get("list"));
        @SuppressWarnings("unchecked")
        Map<String, Object> mm = (Map<String, Object>) m.get("map");
        assertEquals(Long.valueOf(1), mm.get("a"));
        assertEquals("two", mm.get("b"));
    }

    @Test
    void multiDocument() {
        List<Object> docs = YamlUtil.parseDocuments("a: 1\n---\nb: 2");
        assertEquals(2, docs.size());
        assertEquals(Map.of("a", 1L), docs.get(0));
        assertEquals(Map.of("b", 2L), docs.get(1));
    }

    @Test
    void sequenceOfMappings() {
        Map<String, Object> m = YamlUtil.parseObject(
                "items:\n  - name: x\n    id: 1\n  - name: y\n    id: 2");
        @SuppressWarnings("unchecked")
        List<Object> items = (List<Object>) m.get("items");
        assertEquals(2, items.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) items.get(0);
        assertEquals("x", first.get("name"));
        assertEquals(Long.valueOf(1), first.get("id"));
    }

    @Test
    void topLevelSequence() {
        List<Object> list = YamlUtil.parseArray("- 1\n- 2\n- 3\n");
        assertEquals(List.of(1L, 2L, 3L), list);
    }

    @Test
    void roundTrip() {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("name", "flora");
        m.put("nested", Map.of("a", 1L, "b", List.of("x", "y")));
        String yaml = YamlUtil.toYamlString(m);
        Map<String, Object> back = YamlUtil.parseObject(yaml);
        assertEquals(m, back);
    }

    @Test
    void invalidFlowThrows() {
        assertThrows(IllegalStateException.class, () -> YamlUtil.parse("a: [1,2"));
    }
}
