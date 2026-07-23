package com.flora.codec;

import com.flora.java.converter.CollectionConverter;
import com.flora.java.Converter;
import com.flora.java.converter.ConverterRegistry;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.jupiter.api.Assertions.*;

/**
 * CollectionConverter 的完整单测。
 * 覆盖：集合间转换、元素类型转换、单值/数组转集合、各种集合接口映射、反射实例化、异常场景。
 */
class CollectionConverterTest {

    private final CollectionConverter converter = new CollectionConverter();

    /**
     * 测试 null 输入返回 null。
     */
    @Test
    void nullInputReturnsNull() {
        assertNull(converter.convert(null, List.class));
    }

    /**
     * 测试 List 转 Set。
     */
    @Test
    void listToSet() {
        Set<?> result = (Set<?>) converter.convert(List.of(1, 2, 3), Set.class);
        assertEquals(Set.of(1, 2, 3), result);
    }

    /**
     * 测试含元素类型转换的集合间转换。
     */
    @Test
    void elementTypeConversionViaFacade() {
        List<?> result = (List<?>) converter.convert(
                List.of("1", "2", "3"), List.class, Integer.class);
        assertEquals(List.of(1, 2, 3), result);
    }

    /**
     * 测试目标类型可赋值时返回自身。
     */
    @Test
    void listToSameInstanceWhenAssignable() {
        List<String> src = new ArrayList<>(List.of("a"));
        assertSame(src, converter.convert(src, List.class));
    }

    /**
     * 测试单值转 List。
     */
    @Test
    void singleValueToList() {
        assertEquals(List.of("x"), converter.convert("x", List.class));
    }

    /**
     * 测试数组源转 List。
     */
    @Test
    void arraySourceToList() {
        assertEquals(List.of("a", "b"), converter.convert(new String[]{"a", "b"}, List.class));
    }

    /**
     * 测试 SortedSet 接口映射。
     */
    @Test
    void sortedSetMapping() {
        SortedSet<?> result = (SortedSet<?>) converter.convert(List.of(3, 1, 2), SortedSet.class);
        assertInstanceOf(SortedSet.class, result);
        assertEquals(List.of(1, 2, 3), new ArrayList<>(result));
    }

    /**
     * 测试 Queue 接口映射。
     */
    @Test
    void queueMapping() {
        Queue<?> result = (Queue<?>) converter.convert(List.of(1, 2), Queue.class);
        assertInstanceOf(ArrayDeque.class, result);
        assertEquals(List.of(1, 2), new ArrayList<>(result));
    }

    /**
     * 测试 LinkedList 的反射实例化。
     */
    @Test
    void linkedListReflectiveInstantiation() {
        Object result = converter.convert(List.of("a"), LinkedList.class);
        assertInstanceOf(LinkedList.class, result);
        assertEquals(List.of("a"), new ArrayList<>((List<?>) result));
    }

    /**
     * 测试原始 Collection 接口的友好处理。
     */
    @Test
    void rawCollectionInterfaceFriendly() {
        Object result = converter.convert(1, java.util.Collection.class);
        assertInstanceOf(ArrayList.class, result);
    }

    /**
     * 测试不支持的集合类型抛出异常。
     */
    @Test
    void unsupportedCollectionTypeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> converter.convert(List.of(1), java.util.concurrent.BlockingQueue.class));
    }

    /**
     * 测试 NavigableSet 接口映射。
     */
    @Test
    void navigableSetMapping() {
        Object result = converter.convert(List.of(3, 1, 2), NavigableSet.class);
        assertInstanceOf(TreeSet.class, result);
    }

    /**
     * 测试 Deque 接口映射。
     */
    @Test
    void dequeMapping() {
        Object result = converter.convert(List.of(1, 2), Deque.class);
        assertInstanceOf(ArrayDeque.class, result);
    }

    // ========== TargetMatcher 行为验证 ==========

    /**
     * 验证不接受元素类型仍为集合的嵌套集合目标。
     */
    @Test
    void matcherRejectsNestedCollectionElementType() {
        ConverterRegistry registry = ConverterRegistry.newInstance();
        assertNull(registry.find(ArrayList.class, java.util.List.class, ArrayList.class),
                "不应接受元素类型仍为集合的嵌套集合目标");
    }

    /**
     * 验证普通元素类型可匹配 CollectionConverter。
     */
    @Test
    void matcherAcceptsOrdinaryElementType() {
        ConverterRegistry registry = ConverterRegistry.newInstance();
        assertNotNull(registry.find(ArrayList.class, java.util.List.class, Integer.class),
                "普通元素类型应匹配 CollectionConverter");
    }

    /**
     * 验证未指定元素类型时可匹配 CollectionConverter。
     */
    @Test
    void matcherAcceptsNoElementType() {
        ConverterRegistry registry = ConverterRegistry.newInstance();
        assertNotNull(registry.find(ArrayList.class, java.util.Set.class, null),
                "未指定元素类型时应匹配 CollectionConverter");
    }

    // ========== convert(Object, Class, Class) 方法 ==========

    /**
     * 测试带元素类型的 convert 方法。
     */
    @Test
    void convertWithElementType() {
        @SuppressWarnings("unchecked")
        List<Integer> result = (List<Integer>) converter.convert(
                List.of("1", "2"), List.class, Integer.class);
        assertEquals(List.of(1, 2), result);
    }

    /**
     * 测试 null 元素类型时回退。
     */
    @Test
    void convertWithNullElementTypeFallsBack() {
        Object result = converter.convert(List.of("a", "b"), List.class, (Class<?>) null);
        assertEquals(List.of("a", "b"), result);
    }

    // ========== Iterable 来源 ==========

    /**
     * 测试 Iterable 源类型转换。
     */
    @Test
    void iterableSource() {
        Iterable<String> iterable = List.of("a", "b");
        Object result = converter.convert(iterable, List.class);
        assertEquals(List.of("a", "b"), result);
    }

    // ========== CopyOnWriteArrayList 反射实例化 ==========

    /**
     * 测试 CopyOnWriteArrayList 的反射实例化。
     */
    @Test
    void copyOnWriteArrayListInstantiation() {
        Object result = converter.convert(List.of("x"), CopyOnWriteArrayList.class);
        assertInstanceOf(CopyOnWriteArrayList.class, result);
        assertEquals(List.of("x"), new ArrayList<>((Collection<?>) result));
    }
}
