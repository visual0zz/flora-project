package com.flora.java;

import com.flora.java.clazz.InheritTreeTraverser;
import com.flora.java.clazz.InheritTreeTraverser.TraversalAction;
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
            return TraversalAction.CONTINUE;
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

    // ==================== TraversalAction.PRUNE ====================

    /**
     * PRUNE 应阻止当前类型的父类型被加入遍历队列。
     */
    @Test
    void pruneSkipsParentsOfPrunedNode() {
        Set<Class<?>> visited = new HashSet<>();
        InheritTreeTraverser.traverse(ArrayList.class, (type, level) -> {
            visited.add(type);
            return level == 0 ? TraversalAction.PRUNE : TraversalAction.CONTINUE;
        });
        // PRUNE 在 level 0，所以只有 ArrayList 被访问
        assertEquals(Set.of(ArrayList.class), visited);
    }

    /**
     * PRUNE 仅跳过当前分支，不影响队列中其他类型。
     */
    @Test
    void pruneDoesNotAffectOtherBranches() {
        Set<Class<?>> visited = new HashSet<>();
        InheritTreeTraverser.traverse(ArrayList.class, (type, level) -> {
            visited.add(type);
            // 只剪枝 RandomAccess 分支，List 等其他接口应继续遍历
            if (type == RandomAccess.class) {
                return TraversalAction.PRUNE;
            }
            return TraversalAction.CONTINUE;
        });
        // List 仍应被访问（未被 PRUNE 影响）
        assertTrue(visited.contains(List.class));
        // Collection 经 List 路径可达，应被访问
        assertTrue(visited.contains(Collection.class));
    }

    // ==================== TraversalAction.TERMINATE ====================

    /**
     * TERMINATE 应立即停止整个遍历。
     */
    @Test
    void terminateStopsEntireTraversal() {
        Set<Class<?>> visited = new HashSet<>();
        InheritTreeTraverser.traverse(ArrayList.class, (type, level) -> {
            visited.add(type);
            return type == AbstractList.class
                    ? TraversalAction.TERMINATE
                    : TraversalAction.CONTINUE;
        });
        // AbstractList 之后不应再有任何类型被访问
        assertTrue(visited.contains(ArrayList.class));
        assertTrue(visited.contains(AbstractList.class));
        // AbstractCollection 在 TERMINATE 之后，不应被访问
        assertFalse(visited.contains(AbstractCollection.class));
    }

    /**
     * TERMINATE 在根节点立即终止，只访问根。
     */
    @Test
    void terminateAtRoot() {
        Set<Class<?>> visited = new HashSet<>();
        InheritTreeTraverser.traverse(ArrayList.class, (type, level) -> {
            visited.add(type);
            return TraversalAction.TERMINATE;
        });
        assertEquals(Set.of(ArrayList.class), visited);
    }

    // ==================== null 视为 CONTINUE ====================

    /**
     * 返回 null 应等效于 CONTINUE。
     */
    @Test
    void nullReturnTreatedAsContinue() {
        Map<Class<?>, Integer> levels = new LinkedHashMap<>();
        InheritTreeTraverser.traverse(ArrayList.class, (type, level) -> {
            levels.put(type, level);
            return null;  // 应被视为 CONTINUE
        });
        assertTrue(levels.containsKey(AbstractList.class));
        assertTrue(levels.containsKey(List.class));
        assertTrue(levels.containsKey(Object.class));
    }

    /**
     * 全部返回 null 仍能遍历完整棵树（等价于 always CONTINUE）。
     */
    @Test
    void allNullStillTraversesFully() {
        Map<Class<?>, Integer> levels = new LinkedHashMap<>();
        InheritTreeTraverser.traverse(List.class, (type, level) -> {
            levels.put(type, level);
            return null;
        });
        assertEquals(4, levels.size()); // List, SequencedCollection, Collection, Iterable
        assertTrue(levels.containsKey(Iterable.class));
    }
}
