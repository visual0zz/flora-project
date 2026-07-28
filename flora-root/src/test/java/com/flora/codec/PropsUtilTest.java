package com.flora.codec;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PropsUtil 解析与序列化的单元测试。
 */
class PropsUtilTest {

    @Test
    void basicKeyValue() {
        Map<String, Object> m = PropsUtil.parse("a=1\nb=hello\nc=true");
        assertEquals("1", m.get("a"));
        assertEquals("hello", m.get("b"));
        assertEquals("true", m.get("c"));
    }

    @Test
    void separators() {
        assertEquals("v1", PropsUtil.parse("k1=v1").get("k1"));
        assertEquals("v2", PropsUtil.parse("k2: v2").get("k2"));
        assertEquals("v3", PropsUtil.parse("k3 v3").get("k3"));
        assertEquals("v4", PropsUtil.parse("k4 = v4").get("k4"));
    }

    @Test
    void commentsIgnored() {
        Map<String, Object> m = PropsUtil.parse("# full line comment\n! bang comment\nx=1\n  # indented comment\ny=2");
        assertEquals(2, m.size());
        assertEquals("1", m.get("x"));
        assertEquals("2", m.get("y"));
    }

    @Test
    void dottedKeysExpandToNested() {
        Map<String, Object> m = PropsUtil.parse("db.url=jdbc\npg.db.pool.max=10\npg.db.pool.min=2");
        @SuppressWarnings("unchecked")
        Map<String, Object> db = (Map<String, Object>) m.get("db");
        assertEquals("jdbc", db.get("url"));
        @SuppressWarnings("unchecked")
        Map<String, Object> pgMap = (Map<String, Object>) m.get("pg");
        @SuppressWarnings("unchecked")
        Map<String, Object> dbMap = (Map<String, Object>) pgMap.get("db");
        Map<String, Object> pool = (Map<String, Object>) dbMap.get("pool");
        assertEquals("10", pool.get("max"));
        assertEquals("2", pool.get("min"));
    }

    @Test
    void lineContinuation() {
        Map<String, Object> m = PropsUtil.parse("long=line one \\\nline two\nend=ok");
        assertEquals("line one line two", m.get("long"));
        assertEquals("ok", m.get("end"));
    }

    @Test
    void escapes() {
        char bs = '\\';
        // 运行时拼接出「反斜杠 + u + 4 位十六进制」，避免源码里直接写该序列被编译器当作 Unicode 转义
        String input = "eq\\=sign=x\nuni=" + bs + "u0041" + bs + "u0042\ntab=\\t";
        Map<String, Object> m = PropsUtil.parse(input);
        assertEquals("x", m.get("eq=sign"));
        assertEquals("AB", m.get("uni"));
        assertEquals("\t", m.get("tab"));
    }

    @Test
    void emptyValue() {
        Map<String, Object> m = PropsUtil.parse("k1=\nk2");
        assertEquals("", m.get("k1"));
        assertEquals("", m.get("k2"));
    }

    @Test
    void roundTrip() {
        String text = "a=1\nb.c=hello\nb.d=world";
        Map<String, Object> m = PropsUtil.parse(text);
        String out = PropsUtil.toPropertiesString(m);
        // 重新解析应保持结构等价
        Map<String, Object> m2 = PropsUtil.parse(out);
        assertEquals(m, m2);
    }

    @Test
    void serializationEscapesSpecial() {
        Map<String, Object> m = Map.of("k=e", "v:al", "list", List.of("a", "b"));
        String out = PropsUtil.toPropertiesString(m);
        // 键中的 = 与值中的 : 应被转义，重新解析后保持一致
        Map<String, Object> back = PropsUtil.parse(out);
        assertEquals("v:al", back.get("k=e"));
    }

    @Test
    void nullInputRejected() {
        assertThrows(IllegalArgumentException.class, () -> PropsUtil.parse(null));
        assertThrows(IllegalArgumentException.class, () -> PropsUtil.toPropertiesString(null));
    }
}
