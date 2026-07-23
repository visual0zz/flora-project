package com.flora.container.tuple;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tuple 元组家族的单元测试。
 * 测试 Tuple1~Tuple20 的创建、类型安全、元素访问、不可变性、迭代、相等性、比较、序列化及原始 Tuple。
 */
class TupleTest {

    // ==================== 创建与访问 ====================

    /**
     * 测试创建 Tuple1 并访问元素。
     */
    @Test
    void of1() {
        Tuple1<String> t = Tuple.of("a");
        assertEquals("a", t.getV1());
        assertEquals("a", t.getValue());
        assertEquals(1, t.size());
    }

    /**
     * 测试创建 Tuple2 并访问左右元素。
     */
    @Test
    void of2() {
        Tuple2<String,Integer> t = Tuple.of("x", 42);
        assertEquals("x", t.getV1());
        assertEquals(Integer.valueOf(42), t.getV2());
        assertEquals("x", t.getLeft());
        assertEquals(Integer.valueOf(42), t.getRight());
        assertEquals(2, t.size());
    }

    /**
     * 测试创建 Tuple3 并访问左中右元素。
     */
    @Test
    void of3() {
        Tuple3<Integer,String,Double> t = Tuple.of(1, "hello", 3.14);
        assertEquals(1, t.getV1());
        assertEquals("hello", t.getV2());
        assertEquals(3.14, t.getV3(), 1e-9);
        assertEquals(Integer.valueOf(1), t.getLeft());
        assertEquals("hello", t.getMid());
        assertEquals(3.14, t.getRight(), 1e-9);
    }

    /**
     * 测试创建 Tuple10 并访问元素。
     */
    @Test
    void of10() {
        Tuple10<Integer,Integer,Integer,Integer,Integer,Integer,Integer,Integer,Integer,Integer> t =
                Tuple.of(1,2,3,4,5,6,7,8,9,10);
        assertEquals(10, t.size());
        assertEquals(Integer.valueOf(5), t.getV5());
        assertEquals(Integer.valueOf(10), t.getV10());
    }

    /**
     * 测试创建 Tuple20 并访问首尾元素。
     */
    @Test
    void of20() {
        Tuple20<Integer,Integer,Integer,Integer,Integer,Integer,Integer,Integer,Integer,Integer,
                Integer,Integer,Integer,Integer,Integer,Integer,Integer,Integer,Integer,Integer> t =
                Tuple.of(1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20);
        assertEquals(20, t.size());
        assertEquals(Integer.valueOf(1), t.getV1());
        assertEquals(Integer.valueOf(20), t.getV20());
    }

    /**
     * 测试元组的类型安全。
     */
    @Test
    void typeSafety() {
        Tuple2<String,Integer> t = Tuple.of("hello", 99);
        String s = t.getV1();
        Integer i = t.getV2();
        assertEquals("hello", s);
        assertEquals(Integer.valueOf(99), i);
    }

    // ==================== 通用元组方法 ====================

    /**
     * 测试 size 方法返回元素个数。
     */
    @Test
    void size() {
        assertEquals(0, new Tuple().size());
        assertEquals(3, Tuple.of(1, "a", 3.0).size());
    }

    /**
     * 测试通过序号访问元素。
     */
    @Test
    void getVn() {
        Tuple t = Tuple.of("a", "b", "c");
        assertEquals("a", t.getVn(1));
        assertEquals("b", t.getVn(2));
        assertEquals("c", t.getVn(3));
    }

    /**
     * 测试序号越界时抛出异常。
     */
    @Test
    void getVnOutOfBounds() {
        Tuple t = Tuple.of("x");
        assertThrows(IndexOutOfBoundsException.class, () -> t.getVn(0));
        assertThrows(IndexOutOfBoundsException.class, () -> t.getVn(2));
    }

    // ==================== 不可变操作 ====================

    /**
     * 测试 clone 返回自身（元组不可变）。
     */
    @Test
    void cloneReturnsThis() {
        Tuple2<String,Integer> t = Tuple.of("a", 1);
        assertSame(t, t.clone());
    }

    /**
     * 测试 withV1 创建新副本并替换 V1。
     */
    @Test
    void withV1() {
        Tuple3<Integer,String,Double> t = Tuple.of(1, "hello", 3.14);
        Tuple3<Integer,String,Double> t2 = t.withV1(99);
        assertEquals(Integer.valueOf(99), t2.getV1());
        assertEquals("hello", t2.getV2());
        assertEquals(3.14, t2.getV3(), 1e-9);
        assertEquals(Integer.valueOf(1), t.getV1());
        assertNotSame(t, t2);
    }

    /**
     * 测试 withV2 创建新副本并替换 V2。
     */
    @Test
    void withV2() {
        Tuple3<Integer,String,Double> t = Tuple.of(1, "hello", 3.14);
        Tuple3<Integer,String,Double> t2 = t.withV2("world");
        assertEquals(Integer.valueOf(1), t2.getV1());
        assertEquals("world", t2.getV2());
        assertEquals(3.14, t2.getV3(), 1e-9);
    }

    /**
     * 测试 withV3 创建新副本并替换 V3。
     */
    @Test
    void withV3() {
        Tuple3<Integer,String,Double> t = Tuple.of(1, "hello", 3.14);
        Tuple3<Integer,String,Double> t2 = t.withV3(2.71);
        assertEquals(Integer.valueOf(1), t2.getV1());
        assertEquals("hello", t2.getV2());
        assertEquals(2.71, t2.getV3(), 1e-9);
    }

    /**
     * 测试 withV20 创建新副本并替换 V20。
     */
    @Test
    void withV20() {
        Tuple20<Integer,Integer,Integer,Integer,Integer,Integer,Integer,Integer,Integer,Integer,
                Integer,Integer,Integer,Integer,Integer,Integer,Integer,Integer,Integer,Integer> t =
                Tuple.of(1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20);
        var t2 = t.withV20(999);
        assertEquals(Integer.valueOf(999), t2.getV20());
        assertEquals(Integer.valueOf(20), t.getV20()); // 原值不变
    }

    /**
     * 测试 toArray 返回防御性副本。
     */
    @Test
    void toArrayReturnsDefensiveCopy() {
        Tuple2<String,Integer> t = Tuple.of("a", 1);
        Object[] a1 = t.toArray();
        Object[] a2 = t.toArray();
        assertArrayEquals(new Object[]{"a", 1}, a1);
        assertNotSame(a1, a2); // 每次返回新数组
    }

    /**
     * 测试 toList 返回不可修改的列表。
     */
    @Test
    void toListReturnsDefensiveCopy() {
        Tuple2<String,Integer> t = Tuple.of("a", 1);
        List<Object> list = t.toList();
        assertEquals(Arrays.asList("a", 1), list);
        assertThrows(UnsupportedOperationException.class, () -> list.add("x"));
    }

    /**
     * 验证元组不可变性：修改 toArray 结果不影响原元组。
     */
    @Test
    void immutabilityProof() {
        Tuple2<String,Integer> t = Tuple.of("a", 1);
        Object[] arr = t.toArray();
        arr[0] = "changed";
        assertEquals("a", t.getV1());
    }

    // ==================== 迭代 ====================

    /**
     * 测试元组支持迭代器遍历。
     */
    @Test
    void iterator() {
        Tuple3<Integer,String,Double> t = Tuple.of(1, "hello", 3.14);
        List<Object> collected = new ArrayList<>();
        for (Object o : t) {
            collected.add(o);
        }
        assertEquals(Arrays.asList(1, "hello", 3.14), collected);
    }

    /**
     * 测试元组支持 forEach 遍历。
     */
    @Test
    void iterableForEach() {
        Tuple3<Integer,String,Double> t = Tuple.of(1, "hello", 3.14);
        List<Object> collected = new ArrayList<>();
        t.forEach(collected::add);
        assertEquals(Arrays.asList(1, "hello", 3.14), collected);
    }

    /**
     * 测试元组支持 Spliterator。
     */
    @Test
    void spliterator() {
        Tuple3<Integer,String,Double> t = Tuple.of(1, "hello", 3.14);
        Spliterator<Object> sp = t.spliterator();
        List<Object> collected = new ArrayList<>();
        sp.forEachRemaining(collected::add);
        assertEquals(Arrays.asList(1, "hello", 3.14), collected);
    }

    /**
     * 测试元组支持 Stream。
     */
    @Test
    void stream() {
        Tuple3<Integer,String,Double> t = Tuple.of(1, "hello", 3.14);
        List<Object> collected = t.stream().toList();
        assertEquals(Arrays.asList(1, "hello", 3.14), collected);
    }

    // ==================== equals / hashCode ====================

    /**
     * 测试相同内容的元组相等且 hashCode 相同。
     */
    @Test
    void equalsSame() {
        Tuple3<Integer,String,Double> a = Tuple.of(1, "x", 3.0);
        Tuple3<Integer,String,Double> b = Tuple.of(1, "x", 3.0);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    /**
     * 测试不同内容的元组不相等。
     */
    @Test
    void equalsDifferent() {
        Tuple3<Integer,String,Double> a = Tuple.of(1, "x", 3.0);
        Tuple3<Integer,String,Double> b = Tuple.of(1, "y", 3.0);
        assertNotEquals(a, b);
    }

    /**
     * 测试不同长度元组不相等。
     */
    @Test
    void equalsDifferentLength() {
        Tuple2<String,Integer> a = Tuple.of("x", 1);
        Tuple3<String,Integer,Integer> b = Tuple.of("x", 1, 0);
        assertNotEquals(a, b);
    }

    /**
     * 测试含 null 元素的元组相等性。
     */
    @Test
    void equalsWithNull() {
        Tuple2<String,String> a = Tuple.of("a", null);
        Tuple2<String,String> b = Tuple.of("a", null);
        assertEquals(a, b);
    }

    /**
     * 测试元组与自身相等。
     */
    @Test
    void equalsSelf() {
        Tuple2<String,Integer> t = Tuple.of("x", 1);
        assertEquals(t, t);
    }

    /**
     * 测试元组与 null 不相等。
     */
    @Test
    void equalsNull() {
        Tuple2<String,Integer> t = Tuple.of("x", 1);
        assertNotEquals(null, t);
    }

    // ==================== toString ====================

    /**
     * 测试 toString 格式为尖括号包裹的元素列表。
     */
    @Test
    void toStringFormat() {
        assertEquals("<a,b,c>", Tuple.of("a", "b", "c").toString());
        assertEquals("<1,hello,3.14>", Tuple.of(1, "hello", 3.14).toString());
    }

    /**
     * 测试含 null 元素的 toString。
     */
    @Test
    void toStringWithNull() {
        assertEquals("<a,null>", Tuple.of("a", (String)null).toString());
    }

    /**
     * 测试单元素元组的 toString。
     */
    @Test
    void toStringSingle() {
        assertEquals("<x>", Tuple.of("x").toString());
    }

    /**
     * 测试空元组的 toString。
     */
    @Test
    void toStringEmpty() {
        assertEquals("<>", new Tuple().toString());
    }

    // ==================== Comparable ====================

    /**
     * 测试相等元组的比较结果为 0。
     */
    @Test
    void compareToEqual() {
        assertEquals(0, Tuple.of(1, 2).compareTo(Tuple.of(1, 2)));
    }

    /**
     * 测试按首元素比较。
     */
    @Test
    void compareToFirstElement() {
        assertTrue(Tuple.of(1, 2).compareTo(Tuple.of(2, 2)) < 0);
        assertTrue(Tuple.of(2, 2).compareTo(Tuple.of(1, 2)) > 0);
    }

    /**
     * 测试按长度比较：短元组小于长元组。
     */
    @Test
    void compareToByLength() {
        assertTrue(Tuple.of(1, 2).compareTo(Tuple.of(1, 2, 3)) < 0);
        assertTrue(Tuple.of(1, 2, 3).compareTo(Tuple.of(1, 2)) > 0);
    }

    /**
     * 测试 null 元素在比较中视为最小。
     */
    @Test
    void compareToNullFirst() {
        assertTrue(Tuple.of((String)null, "b").compareTo(Tuple.of("a", "b")) < 0);
        assertTrue(Tuple.of("a", "b").compareTo(Tuple.of((String)null, "b")) > 0);
    }

    /**
     * 测试双方 null 元素比较相等。
     */
    @Test
    void compareToBothNull() {
        assertEquals(0, Tuple.of((String)null, (String)null)
                .compareTo(Tuple.of((String)null, (String)null)));
    }

    // ==================== null 元素 ====================

    /**
     * 测试元组可以包含 null 元素。
     */
    @Test
    void nullElement() {
        Tuple2<String,String> t = Tuple.of("a", null);
        assertNull(t.getV2());
        assertNull(t.toArray()[1]);
    }

    // ==================== 序列化 ====================

    /**
     * 测试元组的序列化与反序列化。
     */
    @Test
    void serialization() throws Exception {
        Tuple3<String,Integer,Double> original = Tuple.of("hello", 42, 3.14);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(original);
        }
        ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
        @SuppressWarnings("unchecked")
        Tuple3<String,Integer,Double> restored;
        try (ObjectInputStream ois = new ObjectInputStream(bis)) {
            restored = (Tuple3<String,Integer,Double>) ois.readObject();
        }
        assertEquals(original, restored);
        assertEquals(original.size(), restored.size());
        assertEquals("hello", restored.getV1());
        assertEquals(Integer.valueOf(42), restored.getV2());
        assertEquals(3.14, restored.getV3(), 1e-9);
    }

    // ==================== 原始 Tuple ====================

    /**
     * 测试通过原始 Tuple 构造器创建元组。
     */
    @Test
    void rawTuple() {
        Tuple t = new Tuple("a", 1, 3.0);
        assertEquals(3, t.size());
        assertEquals("a", t.getVn(1));
        assertEquals(1, t.getVn(2));
        assertEquals(3.0, t.getVn(3));
    }

    /**
     * 测试 null 数组作为构造参数抛出异常。
     */
    @Test
    void rawTupleNullRejected() {
        assertThrows(NullPointerException.class, () -> new Tuple((Object[]) null));
    }

    // ==================== 具名访问器 ====================

    /**
     * 测试 Tuple1.getValue()。
     */
    @Test
    void tuple1GetValue() {
        Tuple1<String> t = Tuple.of("only");
        assertEquals("only", t.getValue());
    }

    /**
     * 测试 Tuple2.getLeft() 和 getRight()。
     */
    @Test
    void tuple2LeftRight() {
        Tuple2<String,Integer> t = Tuple.of("left", 42);
        assertEquals("left", t.getLeft());
        assertEquals(Integer.valueOf(42), t.getRight());
    }

    /**
     * 测试 Tuple3.getLeft()、getMid() 和 getRight()。
     */
    @Test
    void tuple3LeftMidRight() {
        Tuple3<String,Integer,Double> t = Tuple.of("L", 2, 3.0);
        assertEquals("L", t.getLeft());
        assertEquals(Integer.valueOf(2), t.getMid());
        assertEquals(3.0, t.getRight(), 1e-9);
    }

    // ── 边界情况 ──

    @Test
    void tupleWithNullElements() {
        Tuple2<String,Integer> t = Tuple.of(null, 42);
        assertNull(t.getV1());
        assertEquals(Integer.valueOf(42), t.getV2());
    }

    @Test
    void getVnNegativeIndexThrows() {
        Tuple2<String,Integer> t = Tuple.of("a", 1);
        assertThrows(IndexOutOfBoundsException.class, () -> t.getVn(0));
        assertThrows(IndexOutOfBoundsException.class, () -> t.getVn(-1));
    }

    @Test
    void getVnIndexTooLargeThrows() {
        Tuple2<String,Integer> t = Tuple.of("a", 1);
        assertThrows(IndexOutOfBoundsException.class, () -> t.getVn(3));
    }

    @Test
    void nullComparison() {
        Tuple2<String,String> a = Tuple.of("a", null);
        Tuple2<String,String> b = Tuple.of("a", "b");
        assertTrue(a.compareTo(b) < 0);
        assertTrue(b.compareTo(a) > 0);
    }

    @Test
    void compareToDifferentSize() {
        Tuple2<String,String> a = Tuple.of("a", "b");
        Tuple1<String> b = Tuple.of("a");
        assertTrue(a.compareTo(b) > 0);
        assertTrue(b.compareTo(a) < 0);
    }
}
