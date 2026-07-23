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
