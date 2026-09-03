package com.flora.sanctum.core.model.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FractionalIndexTest {

    @Test
    void constants() {
        assertEquals(8_589_934_592.0, FractionalIndex.D);
        assertEquals(2.0, FractionalIndex.L);
        assertEquals(32, FractionalIndex.X);
    }

    @Test
    void initialOrderAppendsAfterMax() {
        assertEquals(FractionalIndex.D, FractionalIndex.initialOrder(0.0));
        assertEquals(100.0 + FractionalIndex.D, FractionalIndex.initialOrder(100.0));
    }

    @Test
    void betweenComputesMidpoint() {
        // 插到首位之前：0 与首位中点
        assertEquals(FractionalIndex.D / 2.0, FractionalIndex.between(0.0, FractionalIndex.D), 0.0);
        // 普通中点
        assertEquals((10.0 + FractionalIndex.D) / 2.0, FractionalIndex.between(10.0, FractionalIndex.D), 0.0);
        // 插到末尾
        assertEquals(FractionalIndex.D + FractionalIndex.D, FractionalIndex.between(FractionalIndex.D, null), 0.0);
    }

    @Test
    void collapsedOnlyWhenNoRepresentableMidpoint() {
        assertFalse(FractionalIndex.collapsed(0.0, FractionalIndex.D));
        double a = 1.0;
        double b = Double.longBitsToDouble(Double.doubleToLongBits(a) + 1); // 相邻可表示 double
        assertTrue(FractionalIndex.collapsed(a, b), "相邻 double 无可表示中点");
    }

    @Test
    void doubleRoundTripViaLongBits() {
        double[] values = {FractionalIndex.D, FractionalIndex.D / 2.0, 123.456, 1e15, 8.0 * FractionalIndex.D};
        for (double v : values) {
            assertEquals(v, Double.longBitsToDouble(Double.doubleToLongBits(v)), 0.0);
        }
    }
}
