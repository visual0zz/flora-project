package com.flora.root.container.order;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LongFractionalIndexTest {

    @Test
    void firstIsBetweenSentinels() {
        long f = LongFractionalIndex.INSTANCE.first();
        assertEquals(0L, f); // between(MIN, MAX) 防溢出中点为 0
        assertTrue(f > Long.MIN_VALUE);
        assertTrue(f < Long.MAX_VALUE);
    }

    @Test
    void betweenProducesStrictOrder() {
        Long a = 100L, b = 200L;
        Long c = LongFractionalIndex.INSTANCE.between(a, b);
        assertTrue(LongFractionalIndex.INSTANCE.compare(a, c) < 0);
        assertTrue(LongFractionalIndex.INSTANCE.compare(c, b) < 0);
    }

    @Test
    void frontAndBackInsertion() {
        Long first = LongFractionalIndex.INSTANCE.first();
        Long head = LongFractionalIndex.INSTANCE.between(null, first);
        assertTrue(head < first);
        Long tail = LongFractionalIndex.INSTANCE.between(first, null);
        assertTrue(tail > first);
    }

    @Test
    void comparisonContract() {
        assertEquals(0, LongFractionalIndex.INSTANCE.compare(5L, 5L));
        assertTrue(LongFractionalIndex.INSTANCE.compare(5L, 9L) < 0);
        assertTrue(LongFractionalIndex.INSTANCE.compare(9L, 5L) > 0);
        assertEquals(-1, LongFractionalIndex.INSTANCE.compare(null, 5L));
        assertEquals(1, LongFractionalIndex.INSTANCE.compare(5L, null));
        assertEquals(0, LongFractionalIndex.INSTANCE.compare(null, null));
    }

    @Test
    void nBetweenIsSortedAndWithinBounds() {
        List<Long> keys = LongFractionalIndex.INSTANCE.nBetween(0L, 1000L, 10);
        assertEquals(10, keys.size());
        List<Long> sorted = new ArrayList<>(keys);
        sorted.sort(Long::compareTo);
        assertEquals(keys, sorted);
        for (Long k : keys) {
            assertTrue(k > 0L && k < 1000L);
        }
    }

    @Test
    void adjacentKeysThrowOnInsert() {
        assertThrows(IllegalStateException.class, () -> LongFractionalIndex.INSTANCE.between(41L, 42L));
    }

    @Test
    void overflowSafeMidpoint() {
        long m = LongFractionalIndex.INSTANCE.between(Long.MIN_VALUE, 0L);
        assertTrue(m < 0L);
        assertTrue(m > Long.MIN_VALUE);

        long m2 = LongFractionalIndex.INSTANCE.between(0L, Long.MAX_VALUE);
        assertTrue(m2 > 0L);
        assertTrue(m2 < Long.MAX_VALUE);
    }

    @Test
    void jitteredEqualsBetween() {
        assertEquals(LongFractionalIndex.INSTANCE.between(10L, 20L),
                LongFractionalIndex.INSTANCE.betweenJittered(10L, 20L));
    }

    @Test
    void isValid() {
        assertTrue(LongFractionalIndex.INSTANCE.isValid(123L));
        assertFalse(LongFractionalIndex.INSTANCE.isValid(null));
    }
}
