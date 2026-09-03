package com.flora.sanctum.core.model.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FractionalIndexTest {

    @Test
    void constants() {
        assertEquals(8_589_934_592L, FractionalIndex.D);
        assertEquals(2L, FractionalIndex.L);
        assertEquals(32, FractionalIndex.X);
    }

    @Test
    void initialOrderAppendsAfterMax() {
        assertEquals(FractionalIndex.D, FractionalIndex.initialOrder(0L));
        assertEquals(100L + FractionalIndex.D, FractionalIndex.initialOrder(100L));
    }

    @Test
    void betweenComputesMidpoint() {
        // 插到首位之前：0 与首位中点
        assertEquals(FractionalIndex.D / 2L, FractionalIndex.between(0L, FractionalIndex.D));
        // 普通中点
        assertEquals(10L + (FractionalIndex.D - 10L) / 2L, FractionalIndex.between(10L, FractionalIndex.D));
        // 插到末尾
        assertEquals(FractionalIndex.D + FractionalIndex.D, FractionalIndex.between(FractionalIndex.D, null));
    }

    /** (a+b)/2 在两者都接近 Long.MAX_VALUE 时会溢出成负数，必须走 a + (b-a)/2。 */
    @Test
    void betweenDoesNotOverflowNearMaxValue() {
        long a = Long.MAX_VALUE - 100L;
        long b = Long.MAX_VALUE;
        long mid = FractionalIndex.between(a, b);
        assertEquals(Long.MAX_VALUE - 50L, mid);
        assertTrue(mid > 0, "溢出会导致中点变负");
        assertTrue(mid > a && mid < b, "中点应严格落在两邻居之间");
        // 反证：朴素 (a+b)/2 确实会溢出
        assertTrue(a + b < 0, "朴素写法 a+b 在本例会溢出为负");
    }

    @Test
    void appendOverflowDetectedBeforeItWraps() {
        assertFalse(FractionalIndex.appendOverflow(0L));
        assertFalse(FractionalIndex.appendOverflow(Long.MAX_VALUE - FractionalIndex.D),
                "last 恰好等于 MAX-D 时 +D 仍不溢出");
        assertTrue(FractionalIndex.appendOverflow(Long.MAX_VALUE - FractionalIndex.D + 1L));
        assertTrue(FractionalIndex.appendOverflow(Long.MAX_VALUE));
    }

    /** L 必须保持 2：间隙为 1 时 (b-a)/2 会退化成 0，新 order 等于旧 order。 */
    @Test
    void collapsedOnlyWhenGapBelowL() {
        assertFalse(FractionalIndex.collapsed(0L, FractionalIndex.D));
        assertFalse(FractionalIndex.collapsed(5L, 7L), "间隙=2 时 (b-a)/2=1，中点合法");
        assertEquals(6L, FractionalIndex.between(5L, 7L), "间隙=2 时中点应为 a+1");
        assertTrue(FractionalIndex.collapsed(5L, 6L), "间隙=1 时中点会退化，必须重排");
    }

    @Test
    void headInsertionHalvesRepeatedly() {
        long first = FractionalIndex.D;
        for (int i = 0; i < FractionalIndex.X; i++) {
            long mid = FractionalIndex.between(0L, first);
            assertTrue(mid > 0 && mid < first, "头部插入的中点应严格在 (0, first) 内");
            first = mid;
        }
        // X = log2(D/L) 次后，间隙恰好缩到 L（阈值上，仍可再插一次）
        assertEquals(FractionalIndex.L, first);
        assertFalse(FractionalIndex.collapsed(0L, first));
        // 再插一次（第 X+1 次）间隙 < L，必须重排
        first = FractionalIndex.between(0L, first);
        assertEquals(1L, first);
        assertTrue(FractionalIndex.collapsed(0L, first), "第 X+1 次头部插入后应触发重排");
    }

}
