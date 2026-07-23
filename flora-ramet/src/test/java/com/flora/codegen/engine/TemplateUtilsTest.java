package com.flora.codegen.engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 覆盖工具类 TemplateUtils 的全部分支：真值、集合展开、属性访问、关键词查找、异常。
 */
class TemplateUtilsTest {

    /** 简单 Java Bean，用于测试反射属性访问。 */
    public static final class Bean {
        public String label = "field";
        private final String name;
        private final boolean active;

        Bean(String name, boolean active) {
            this.name = name;
            this.active = active;
        }

        public String getName() {
            return name;
        }

        public boolean isActive() {
            return active;
        }
    }

    @Test
    void truthyBranches() {
        assertTrue(TemplateUtils.truthy(Boolean.TRUE));
        assertFalse(TemplateUtils.truthy(Boolean.FALSE));
        assertFalse(TemplateUtils.truthy(null));
        assertFalse(TemplateUtils.truthy(""));
        assertFalse(TemplateUtils.truthy("false"));
        assertFalse(TemplateUtils.truthy("x"));
        assertFalse(TemplateUtils.truthy(1));
    }

    @Test
    @SuppressWarnings("unchecked")
    void toListAllShapes() {
        Map<String, Object> map = Map.of("k", 1);
        assertEquals(List.of(Map.entry("k", 1)), TemplateUtils.toList(map));

        List<Object> list = new ArrayList<>(List.of("a", "b"));
        assertEquals(list, TemplateUtils.toList(list));

        Object[] arr = {"x", "y"};
        assertEquals(List.of("x", "y"), TemplateUtils.toList(arr));

        Iterator<Object> it = List.<Object>of(1, 2).iterator();
        assertEquals(List.of(1, 2), TemplateUtils.toList(it));

        assertEquals(List.of(), TemplateUtils.toList(null));
    }

    @Test
    void toListUnsupportedTypeThrows() {
        assertThrows(CodeGenException.class, () -> TemplateUtils.toList(123));
    }

    @Test
    void getPropertyFromMapAndBean() {
        Map<String, Object> m = Map.of("x", 9);
        assertEquals(9, TemplateUtils.getProperty(m, "x"));

        Bean bean = new Bean("Bob", true);
        assertEquals("Bob", TemplateUtils.getProperty(bean, "name"));
        assertEquals(Boolean.TRUE, TemplateUtils.getProperty(bean, "active"));
        assertEquals("field", TemplateUtils.getProperty(bean, "label"));
        assertNull(TemplateUtils.getProperty(bean, "missing"));
        assertNull(TemplateUtils.getProperty(null, "x"));
    }

    @Test
    void indexOfKeywordRespectsDepthAndBoundaries() {
        assertEquals(2, TemplateUtils.indexOfKeyword("a as b", "as"));
        // 位于标识符中间的子串不应匹配
        assertEquals(-1, TemplateUtils.indexOfKeyword("class", "as"));
        // 处于括号（深度不为 0）内不应匹配
        assertEquals(-1, TemplateUtils.indexOfKeyword("foo(as) xyz", "as"));
        // 括号外可匹配
        assertEquals(8, TemplateUtils.indexOfKeyword("foo(as) xyz", "xyz"));
    }

    @Test
    void errBuildsMessageWithLine() {
        CodeGenException e = TemplateUtils.err(5, "boom");
        assertTrue(e.getMessage().contains("第 5 行"));
        assertTrue(e.getMessage().contains("boom"));
    }

    @Test
    void errWithoutLine() {
        CodeGenException e = TemplateUtils.err(-1, "plain");
        assertEquals("plain", e.getMessage());
    }
}
