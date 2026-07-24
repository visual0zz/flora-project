package com.flora.java;

import com.flora.java.clazz.InheritTreeTraverser;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * InheritTreeTraverser 的回归测试。
 * 重点锁定 BFS 最短路径层级语义，并防止之前出现的"遍历末尾递归自调用导致无限递归"回归。
 */
class InheritTreeTraverserTest {

    /**
     * 收集 (type -> level)，同时断言每个类型只被访问一次（BFS 去重）。
     * 若存在无限递归，同一类型会被重复访问，断言失败或栈溢出使测试失败。
     */
    private static Map<Class<?>, Integer> collectLevels(Class<?> root) {
        Map<Class<?>, Integer> levels = new LinkedHashMap<>();
        InheritTreeTraverser.traverse(root, (type, level) -> {
            assertFalse(levels.containsKey(type),
                    "type " + type.getName() + " 被访问多次 -> 可能存在无限递归");
            levels.put(type, level);
        });
        return levels;
    }

    /**
     * null 根不应调用访问器，也不应抛异常。
     */
    @Test
    void nullRootDoesNotInvokeVisitor() {
        Map<Class<?>, Integer> levels = collectLevels(null);
        assertTrue(levels.isEmpty());
    }

    /**
     * 根类型自身报告为 level 0。
     */
    @Test
    void rootIsReportedAtLevelZero() {
        Map<Class<?>, Integer> levels = collectLevels(ArrayList.class);
        assertEquals(0, levels.get(ArrayList.class));
    }

    /**
     * 直接超类与直接接口均为 level 1。
     */
    @Test
    void directParentsAtLevelOne() {
        Map<Class<?>, Integer> levels = collectLevels(ArrayList.class);
        assertEquals(1, levels.get(AbstractList.class));
        assertEquals(1, levels.get(List.class));
        assertEquals(1, levels.get(RandomAccess.class));
        assertEquals(1, levels.get(Cloneable.class));
        assertEquals(1, levels.get(Serializable.class));
    }

    /**
     * 同时存在"父类短链"与"接口长链"时，BFS 应报告最短路径层级。
     * TreeMap -> AbstractMap -> Map 为 2 层；TreeMap -> NavigableMap -> SortedMap -> Map 为 3 层。
     */
    @Test
    void shortestPathChosenOverLongerChain() {
        Map<Class<?>, Integer> levels = collectLevels(TreeMap.class);
        assertEquals(1, levels.get(AbstractMap.class));
        assertEquals(2, levels.get(Map.class));
        assertEquals(1, levels.get(NavigableMap.class));
        assertEquals(2, levels.get(SortedMap.class));
    }

    /**
     * 菱形继承必须终止且去重：HashMap 经父类 AbstractMap 与直接接口都能到达 Map。
     */
    @Test
    void diamondHierarchyTerminatesAndDedupes() {
        Map<Class<?>, Integer> levels = collectLevels(HashMap.class);
        assertTrue(levels.containsKey(Map.class));
        assertEquals(1, levels.get(Map.class)); // 直接接口路径优先
        for (int lvl : levels.values()) {
            assertTrue(lvl >= 0 && lvl < Integer.MAX_VALUE / 2, "unexpected level " + lvl);
        }
    }

    /**
     * 接口没有 superclass，因此遍历接口继承树不会到达 Object（由 InheritDistanceCalculator 特例处理）。
     * 注意 getInterfaces() 只返回直接声明的接口：List 直接声明 SequencedCollection，
     * 而 Collection 是经 SequencedCollection 间接继承的，所以层级为 0/1/2/3。
     */
    @Test
    void interfaceRootHasNoObjectParent() {
        Map<Class<?>, Integer> levels = collectLevels(List.class);
        assertFalse(levels.containsKey(Object.class),
                "接口继承树不应包含 Object");
        assertEquals(0, levels.get(List.class));
        assertEquals(1, levels.get(SequencedCollection.class));
        assertEquals(2, levels.get(Collection.class));
        assertEquals(3, levels.get(Iterable.class));
    }
}
