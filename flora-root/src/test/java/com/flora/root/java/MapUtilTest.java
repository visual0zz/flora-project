package com.flora.root.java;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MapUtil 映射工具类的单元测试。
 * 覆盖 getOrDefault/getOrSupply、putIfAbsent（值/提供器）、invert（含重复值冲突）、
 * filterValues 及 toMap。
 */
class MapUtilTest {

    // ==================== 取值 ====================

    @Test
    void getOrDefaultNullSafe() {
        assertEquals("v", MapUtil.getOrDefault(Map.of("k", "v"), "k", "d"));
        assertEquals("d", MapUtil.getOrDefault(Map.of("k", "v"), "missing", "d"));
        assertEquals("d", MapUtil.getOrDefault(null, "k", "d"));
    }

    @Test
    void getOrSupplyDoesNotStore() {
        Map<String, String> map = new java.util.HashMap<>();
        map.put("k", "v");
        Supplier<String> supplier = () -> "computed";
        assertEquals("v", MapUtil.getOrSupply(map, "k", supplier));
        assertEquals("computed", MapUtil.getOrSupply(map, "missing", supplier));
        assertFalse(map.containsKey("missing"), "getOrSupply 不应写回原映射");
    }

    // ==================== 缺省填充 ====================

    @Test
    void putIfAbsentValue() {
        Map<String, String> map = new java.util.HashMap<>();
        assertEquals("v", MapUtil.putIfAbsent(map, "k", "v"));
        assertEquals("v", MapUtil.putIfAbsent(map, "k", "other"));
        assertEquals("v", map.get("k"));
    }

    // ==================== 反转 ====================

    @Test
    void invertBasic() {
        Map<String, Integer> map = Map.of("a", 1, "b", 2);
        Map<Integer, String> inverted = MapUtil.invert(map);
        assertEquals(Map.of(1, "a", 2, "b"), inverted);
    }

    @Test
    void invertDetectsDuplicateValue() {
        Map<String, Integer> map = Map.of("a", 1, "b", 1);
        assertThrows(IllegalArgumentException.class, () -> MapUtil.invert(map));
    }

    @Test
    void invertNullReturnsEmpty() {
        assertTrue(MapUtil.invert(null).isEmpty());
    }

    // ==================== 过滤 ====================

    @Test
    void filterValuesByPredicate() {
        Map<String, Integer> map = Map.of("a", 1, "b", 2, "c", 3);
        Map<String, Integer> filtered = MapUtil.filterValues(map, v -> v % 2 == 1);
        assertEquals(Map.of("a", 1, "c", 3), filtered);
    }

    @Test
    void filterValuesNullSafeForNullValue() {
        Map<String, String> map = new java.util.HashMap<>();
        map.put("a", null);
        map.put("b", "x");
        Map<String, String> filtered = MapUtil.filterValues(map, v -> v != null);
        assertEquals(Map.of("b", "x"), filtered);
    }

    @Test
    void filterValuesNullReturnsEmpty() {
        assertTrue(MapUtil.filterValues(null, v -> true).isEmpty());
    }

    // ==================== 集合转 Map ====================

    @Test
    void toMapFromIterable() {
        List<String> items = List.of("a", "bb", "ccc");
        Map<String, Integer> map = MapUtil.toMap(items, s -> s, String::length);
        assertEquals(Map.of("a", 1, "bb", 2, "ccc", 3), map);
    }

    @Test
    void toMapNullIterableReturnsEmpty() {
        assertTrue(MapUtil.toMap(null, s -> s, String::length).isEmpty());
    }
}
