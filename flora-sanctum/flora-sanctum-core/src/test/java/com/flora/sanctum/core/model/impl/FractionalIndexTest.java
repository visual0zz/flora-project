package com.flora.sanctum.core.model.impl;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FractionalIndexTest {

    @Test
    void firstIsCompact() {
        assertEquals("a1", FractionalIndex.first());
    }

    @Test
    void afterAppendsByIncrementingFraction() {
        assertEquals("a2", FractionalIndex.after("a1"));
        assertEquals("aA", FractionalIndex.after("a9"));
        assertEquals("b1", FractionalIndex.after("az"), "小数段全满时整数段进一");
        assertEquals("a1", FractionalIndex.after(null), "空列表取首个");
        assertEquals("a1", FractionalIndex.after(""));
    }

    @Test
    void afterGrowsWhenHeadCannotCarry() {
        assertEquals("z1", FractionalIndex.after("z"));
        assertEquals("zz1", FractionalIndex.after("zz"));
        assertEquals("zzzz1", FractionalIndex.after("zzzz"));
    }

    @Test
    void afterNeverEndsWithZero() {
        String k = FractionalIndex.first();
        for (int i = 0; i < 3000; i++) {
            k = FractionalIndex.after(k);
            assertFalse(k.endsWith("0"), "键不应以 0 结尾: " + k);
        }
    }

    @Test
    void afterStaysStrictlyIncreasing() {
        String prev = FractionalIndex.first();
        for (int i = 0; i < 3000; i++) {
            String next = FractionalIndex.after(prev);
            assertTrue(next.compareTo(prev) > 0, next + " 应大于 " + prev);
            prev = next;
        }
    }

    @Test
    void betweenComputesMidpoint() {
        assertEquals("a2", FractionalIndex.between("a1", null), "无后继即追加");
        String head = FractionalIndex.between(null, "a1");
        assertTrue(head.compareTo("a1") < 0, "无前驱时应落在首个之前: " + head);
    }

    @Test
    void betweenFallsStrictlyBetweenNeighbours() {
        String lo = FractionalIndex.first();
        String hi = FractionalIndex.after(lo);
        for (int i = 0; i < 500; i++) {
            String mid = FractionalIndex.between(lo, hi);
            assertTrue(mid.compareTo(lo) > 0, mid + " 应大于 " + lo);
            assertTrue(mid.compareTo(hi) < 0, mid + " 应小于 " + hi);
            if (i % 2 == 0) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
    }

    @Test
    void repeatedHeadInsertionStaysStrictlyDecreasing() {
        String first = FractionalIndex.first();
        for (int i = 0; i < 500; i++) {
            String inserted = FractionalIndex.between(null, first);
            assertTrue(inserted.compareTo(first) < 0, inserted + " 应小于 " + first);
            first = inserted;
        }
    }

    /** 反复在任意位置插入，列表应始终保持全序。 */
    @Test
    void manyInsertsKeepTotalOrder() {
        List<String> keys = new ArrayList<>();
        keys.add(FractionalIndex.first());
        for (int i = 0; i < 500; i++) {
            int at = (i * 7) % keys.size();
            String prev = at == 0 ? null : keys.get(at - 1);
            keys.add(at, FractionalIndex.between(prev, keys.get(at)));
        }
        for (int i = 1; i < keys.size(); i++) {
            assertTrue(keys.get(i - 1).compareTo(keys.get(i)) < 0,
                    "位置 " + i + " 处顺序被破坏: " + keys.get(i - 1) + " / " + keys.get(i));
        }
    }

    @Test
    void rejectsNonAscendingBounds() {
        assertThrows(IllegalArgumentException.class, () -> FractionalIndex.between("a2", "a1"));
        assertThrows(IllegalArgumentException.class, () -> FractionalIndex.between("a1", "a1"));
    }

    @Test
    void rejectsIllegalCharacters() {
        assertThrows(IllegalArgumentException.class, () -> FractionalIndex.after("a-"));
    }
}
