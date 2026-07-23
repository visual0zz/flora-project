package com.flora.fast.container.tuple;

import java.io.Serializable;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FastTupleTest {

    // ── 同质（长度 2） ──

    @Test
    void homogeneousII() {
        var a = new FastTupleII(1, 2);
        assertEquals(1, a.getI1());
        assertEquals(2, a.getI2());
        assertEquals("<1, 2>", a.toString());

        var b = new FastTupleII(1, 2);
        var c = new FastTupleII(1, 3);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    // ── 异质（长度 2） ──

    @Test
    void heterogeneousIL() {
        var a = new FastTupleIL(1, 2L);
        assertEquals(1, a.getI1());
        assertEquals(2L, a.getL1());
        assertEquals("<1, 2>", a.toString());
        assertEquals(a, new FastTupleIL(1, 2L));
        assertNotEquals(a, new FastTupleIL(1, 3L));
    }

    // ── 浮点比较语义（Float.compare / Double.compare） ──

    @Test
    void floatCompareSemantics() {
        assertNotEquals(new FastTupleFF(0.0f, 0.0f), new FastTupleFF(-0.0f, 0.0f));
        assertNotEquals(new FastTupleDD(0.0, 0.0), new FastTupleDD(-0.0, 0.0));
        assertEquals(new FastTupleFF(Float.NaN, 0.0f), new FastTupleFF(Float.NaN, 0.0f));
        assertEquals(new FastTupleDD(Double.NaN, 0.0), new FastTupleDD(Double.NaN, 0.0));
        assertNotEquals(new FastTupleFF(1.0f, 0.0f), new FastTupleFF(2.0f, 0.0f));
    }

    // ── 工厂方法（2 元） ──

    @Test
    void factoryMethods() {
        assertEquals(new FastTupleII(7, 7), FastTuple.of(7, 7));
        assertEquals(new FastTupleIL(1, 2L), FastTuple.of(1, 2L));
        assertEquals(new FastTupleII(1, 2), FastTuple.of(1, 2));
        assertEquals(new FastTupleFF(1.0f, 2.0f), FastTuple.of(1.0f, 2.0f));
        // 验证参数交换：of(long, int) 也应映射到 FastTupleIL
        assertEquals(new FastTupleIL(1, 2L), FastTuple.of(2L, 1));
    }

    // ── 同类型参数顺序约定 ──
    //
    // 约定：对 FastTuple.of(v1, v2, ...)，其中同类型参数 v1、v2 的 getter
    // 编号应反映它们在 of() 输入列表中的顺序。即第一个 long 应该由 getL1() 返回，
    // 第二个 long 应该由 getL2() 返回，以此类推。

    @Test
    void homogeneousIIGetterOrder() {
        var a = new FastTupleII(10, 20);
        assertEquals(10, a.getI1(), "getI1() 应为第一个参数");
        assertEquals(20, a.getI2(), "getI2() 应为第二个参数");
        // 交换输入后 getter 也应跟着变
        var b = new FastTupleII(20, 10);
        assertEquals(20, b.getI1());
        assertEquals(10, b.getI2());
    }

    @Test
    void homogeneousLLGetterOrder() {
        var a = new FastTupleLL(100L, 200L);
        assertEquals(100L, a.getL1(), "getL1() 应为第一个 long 参数");
        assertEquals(200L, a.getL2(), "getL2() 应为第二个 long 参数");
        // 交换验证
        var b = new FastTupleLL(200L, 100L);
        assertEquals(200L, b.getL1());
        assertEquals(100L, b.getL2());
    }

    @Test
    void homogeneousFFGetterOrder() {
        var a = new FastTupleFF(1.5f, 2.5f);
        assertEquals(1.5f, a.getF1(), 0f);
        assertEquals(2.5f, a.getF2(), 0f);
        var b = new FastTupleFF(2.5f, 1.5f);
        assertEquals(2.5f, b.getF1(), 0f);
        assertEquals(1.5f, b.getF2(), 0f);
    }

    @Test
    void homogeneousDDGetterOrder() {
        var a = new FastTupleDD(1.5, 2.5);
        assertEquals(1.5, a.getD1(), 0.0);
        assertEquals(2.5, a.getD2(), 0.0);
        var b = new FastTupleDD(2.5, 1.5);
        assertEquals(2.5, b.getD1(), 0.0);
        assertEquals(1.5, b.getD2(), 0.0);
    }

    @Test
    void homogeneousBBGetterOrder() {
        var a = new FastTupleBB((byte) 1, (byte) 2);
        assertEquals((byte) 1, a.getB1());
        assertEquals((byte) 2, a.getB2());
    }

    @Test
    void homogeneousCCGetterOrder() {
        var a = new FastTupleCC('a', 'b');
        assertEquals('a', a.getC1());
        assertEquals('b', a.getC2());
    }

    @Test
    void homogeneousZZGetterOrder() {
        var a = new FastTupleZZ(true, false);
        assertTrue(a.getZ1());
        assertFalse(a.getZ2());
        var b = new FastTupleZZ(false, true);
        assertFalse(b.getZ1());
        assertTrue(b.getZ2());
    }

    @SuppressWarnings("unused")
    @Test
    void homogeneousIIIGetterOrder() {
        var a = new FastTupleIII(1, 2, 3);
        assertEquals(1, a.getI1());
        assertEquals(2, a.getI2());
        assertEquals(3, a.getI3());
        var b = new FastTupleIII(3, 2, 1);
        assertEquals(3, b.getI1());
        assertEquals(2, b.getI2());
        assertEquals(1, b.getI3());
    }

    @SuppressWarnings("unused")
    @Test
    void homogeneousLLLGetterOrder() {
        var a = new FastTupleLLL(10L, 20L, 30L);
        assertEquals(10L, a.getL1());
        assertEquals(20L, a.getL2());
        assertEquals(30L, a.getL3());
    }

    @Test
    void homogeneousDDDGetterOrder() {
        var a = new FastTupleDDD(1.0, 2.0, 3.0);
        assertEquals(1.0, a.getD1(), 0.0);
        assertEquals(2.0, a.getD2(), 0.0);
        assertEquals(3.0, a.getD3(), 0.0);
    }

    @SuppressWarnings("unused")
    @Test
    void homogeneousFFFFGetterOrder() {
        var a = new FastTupleFFFF(1.0f, 2.0f, 3.0f, 4.0f);
        assertEquals(1.0f, a.getF1(), 0f);
        assertEquals(2.0f, a.getF2(), 0f);
        assertEquals(3.0f, a.getF3(), 0f);
        assertEquals(4.0f, a.getF4(), 0f);
    }

    @SuppressWarnings("unused")
    @Test
    void homogeneousIIIIGetterOrder() {
        var a = new FastTupleIIII(10, 20, 30, 40);
        assertEquals(10, a.getI1());
        assertEquals(20, a.getI2());
        assertEquals(30, a.getI3());
        assertEquals(40, a.getI4());
    }

    @SuppressWarnings("unused")
    @Test
    void homogeneousLLLLGetterOrder() {
        var a = new FastTupleLLLL(1L, 2L, 3L, 4L);
        assertEquals(1L, a.getL1());
        assertEquals(2L, a.getL2());
        assertEquals(3L, a.getL3());
        assertEquals(4L, a.getL4());
    }

    @SuppressWarnings("unused")
    @Test
    void homogeneousDDDDGetterOrder() {
        var a = new FastTupleDDDD(1.0, 2.0, 3.0, 4.0);
        assertEquals(1.0, a.getD1(), 0.0);
        assertEquals(2.0, a.getD2(), 0.0);
        assertEquals(3.0, a.getD3(), 0.0);
        assertEquals(4.0, a.getD4(), 0.0);
    }

    @SuppressWarnings("unused")
    @Test
    void heterogeneousLLFDGetterOrder() {
        // FastTupleLLFD: L(1), L(2), F(3), D(4) — 两个 long 按输入顺序分别对应 getL1/getL2
        var a = new FastTupleLLFD(100L, 200L, 3.0f, 4.0);
        assertEquals(100L, a.getL1(), "第一个 long");
        assertEquals(200L, a.getL2(), "第二个 long");
        assertEquals(3.0f, a.getF1(), 0f, "第一个 float");
        assertEquals(4.0, a.getD1(), 0.0, "第一个 double");
    }

    @SuppressWarnings("unused")
    @Test
    void heterogeneousIFFDGetterOrder() {
        // FastTupleIFFD: I(1), F(2), F(3), D(4) — 两个 float 按输入顺序分别对应 getF1/getF2
        var a = new FastTupleIFFD(10, 20.0f, 30.0f, 40.0);
        assertEquals(10, a.getI1());
        assertEquals(20.0f, a.getF1(), 0f, "第一个 float");
        assertEquals(30.0f, a.getF2(), 0f, "第二个 float");
        assertEquals(40.0, a.getD1(), 0.0);
    }

    // ── of() 工厂方法的参数顺序 ──

    @Test
    void ofPreservesOrderForSameType() {
        // FastTuple.of(long, long) → FastTupleLL，getL1 = 第一个 long
        var ll = FastTuple.of(1L, 2L);
        assertEquals(1L, ll.getL1());
        assertEquals(2L, ll.getL2());

        // FastTuple.of(int, int) → FastTupleII
        var ii = FastTuple.of(10, 20);
        assertEquals(10, ii.getI1());
        assertEquals(20, ii.getI2());

        // FastTuple.of(float, float) → FastTupleFF
        var ff = FastTuple.of(1.0f, 2.0f);
        assertEquals(1.0f, ff.getF1(), 0f);
        assertEquals(2.0f, ff.getF2(), 0f);
    }

    @SuppressWarnings("unused")
    @Test
    void ofSwappedOrderForMixedTypes() {
        // FastTuple.of(long, int) 内部交换 → FastTupleIL: getL1 = 那个 long, getI1 = 那个 int
        var il = FastTuple.of(42L, 7);
        assertEquals(42L, il.getL1(), "of(long, int) 中 long 应由 getL1() 返回");
        assertEquals(7, il.getI1(), "of(long, int) 中 int 应由 getI1() 返回");

        // FastTuple.of(float, int) → FastTupleIF
        var fi = FastTuple.of(3.0f, 5);
        assertInstanceOf(FastTupleIF.class, FastTuple.of(3.0f, 5));
    }

    // ── 与 null 及无关类型 ──

    @Test
    void equalsNullAndSelf() {
        var t = new FastTupleIL(1, 2L);
        assertEquals(t, t);
        assertNotEquals(t, null);
        assertNotEquals(t, "not a tuple");
    }

    @Test
    void serializable() {
        assertTrue(Serializable.class.isAssignableFrom(FastTupleIL.class));
        assertTrue(Serializable.class.isAssignableFrom(FastTupleFF.class));
    }

    // ── hashCode 契约 ──

    @Test
    void hashCodeConsistentWithEquals() {
        var a = new FastTupleIL(1, 2L);
        var b = new FastTupleIL(1, 2L);
        assertEquals(a.hashCode(), b.hashCode());
    }

    // ── toString 边界 ──

    @Test
    void toStringTwoElements() {
        assertEquals("<1, 2>", new FastTupleIL(1, 2L).toString());
    }
}
