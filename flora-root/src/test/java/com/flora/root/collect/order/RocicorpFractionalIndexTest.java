package com.flora.root.collect.order;

import com.flora.root.container.order.RocicorpFractionalIndex;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RocicorpFractionalIndexTest {

    /** 对齐官方实现的示例，作为跨语言实现兼容性的回归基准。 */
    @Test
    void matchesReferenceExamples() {
        String first = RocicorpFractionalIndex.between(null, null);
        assertEquals("a0", first);
        String second = RocicorpFractionalIndex.between(first, null);
        assertEquals("a1", second);
        String third = RocicorpFractionalIndex.between(second, null);
        assertEquals("a2", third);
        assertEquals("Zz", RocicorpFractionalIndex.between(null, first));
        assertEquals("a1V", RocicorpFractionalIndex.between(second, third));
    }

    @Test
    void nBetweenMatchesReferenceExamples() {
        assertEquals(List.of("a0", "a1"), RocicorpFractionalIndex.nBetween(null, null, 2));
        assertEquals(List.of("a2", "a3"), RocicorpFractionalIndex.nBetween("a1", null, 2));
        assertEquals(List.of("Zy", "Zz"), RocicorpFractionalIndex.nBetween(null, "a0", 2));
        assertEquals(List.of("a0G", "a0V"), RocicorpFractionalIndex.nBetween("a0", "a1", 2));
    }

    @Test
    void appendStaysStrictlyIncreasing() {
        String prev = RocicorpFractionalIndex.first();
        for (int i = 0; i < 3000; i++) {
            String next = RocicorpFractionalIndex.between(prev, null);
            assertTrue(next.compareTo(prev) > 0, next + " 应大于 " + prev);
            prev = next;
        }
    }

    @Test
    void prependStaysStrictlyDecreasing() {
        String first = RocicorpFractionalIndex.first();
        for (int i = 0; i < 500; i++) {
            String inserted = RocicorpFractionalIndex.between(null, first);
            assertTrue(inserted.compareTo(first) < 0, inserted + " 应小于 " + first);
            first = inserted;
        }
    }

    /**
     * 变长整数把键长增长摊到 62^n 次插入：整数部分加/减一，只在进位传播到头部时才变长。
     * 连续追加或前插数千次，键长都应保持个位数。
     */
    @Test
    void repeatedAppendsAndPrependsKeepKeysShort() {
        String tail = RocicorpFractionalIndex.first();
        for (int i = 0; i < 3000; i++) {
            tail = RocicorpFractionalIndex.between(tail, null);
        }
        assertTrue(tail.length() <= 4,
                "3000 次追加后键长应仍很短（变长整数），实际 " + tail.length() + " (" + tail + ")");

        String head = RocicorpFractionalIndex.first();
        for (int i = 0; i < 3000; i++) {
            head = RocicorpFractionalIndex.between(null, head);
        }
        assertTrue(head.length() <= 8, "3000 次前插后键长应仍是个位数，实际 " + head.length() + " (" + head + ")");
    }

    /** 反复在任意位置插入，列表应始终保持全序。 */
    @Test
    void manyInsertsKeepTotalOrder() {
        List<String> keys = new ArrayList<>();
        keys.add(RocicorpFractionalIndex.first());
        for (int i = 0; i < 500; i++) {
            int at = (i * 7) % keys.size();
            String prev = at == 0 ? null : keys.get(at - 1);
            keys.add(at, RocicorpFractionalIndex.between(prev, keys.get(at)));
        }
        for (int i = 1; i < keys.size(); i++) {
            assertTrue(keys.get(i - 1).compareTo(keys.get(i)) < 0,
                    "位置 " + i + " 处顺序被破坏: " + keys.get(i - 1) + " / " + keys.get(i));
        }
    }

    @Test
    void nBetweenReturnsSortedDistinctKeysInsideBounds() {
        String lo = "a1";
        String hi = "a2";
        List<String> keys = RocicorpFractionalIndex.nBetween(lo, hi, 20);
        assertEquals(20, keys.size());
        for (int i = 0; i < keys.size(); i++) {
            assertTrue(keys.get(i).compareTo(lo) > 0, keys.get(i) + " 应大于 " + lo);
            assertTrue(keys.get(i).compareTo(hi) < 0, keys.get(i) + " 应小于 " + hi);
            if (i > 0) {
                assertTrue(keys.get(i - 1).compareTo(keys.get(i)) < 0, "nBetween 应返回升序");
            }
        }
    }

    /** jitter：同一区间的并发插入应得到不同但可比较、且都落在区间内的键。 */
    @Test
    void jitteredInsertsInSameGapProduceDistinctKeys() {
        String lo = "a1";
        String hi = "a2";
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String key = RocicorpFractionalIndex.betweenJittered(lo, hi);
            assertTrue(key.compareTo(lo) > 0, key + " 应大于 " + lo);
            assertTrue(key.compareTo(hi) < 0, key + " 应小于 " + hi);
            assertTrue(RocicorpFractionalIndex.isValid(key), "生成的键应合法: " + key);
            seen.add(key);
        }
        assertTrue(seen.size() > 1, "同一区间的多次插入应产生不同的键");
    }

    @Test
    void deterministicInsertsAreStable() {
        assertEquals(RocicorpFractionalIndex.between("a1", "a2"), RocicorpFractionalIndex.between("a1", "a2"));
    }

    @Test
    void validatesKeys() {
        assertTrue(RocicorpFractionalIndex.isValid("a0"));
        assertTrue(RocicorpFractionalIndex.isValid("a1"));
        assertTrue(RocicorpFractionalIndex.isValid("Zz"));
        assertFalse(RocicorpFractionalIndex.isValid(null));
        assertFalse(RocicorpFractionalIndex.isValid(""));
        assertFalse(RocicorpFractionalIndex.isValid("0a0"), "头部必须是 A-Za-z");
        assertFalse(RocicorpFractionalIndex.isValid("a"), "整数部分不完整");
        assertFalse(RocicorpFractionalIndex.isValid("a10"), "小数部分不能以 0 结尾");
        assertFalse(RocicorpFractionalIndex.isValid("A" + "0".repeat(26)), "最小整数不可再向前生成");
    }

    @Test
    void rejectsOutOfOrderBounds() {
        assertThrows(IllegalArgumentException.class, () -> RocicorpFractionalIndex.between("a2", "a1"));
        assertThrows(IllegalArgumentException.class, () -> RocicorpFractionalIndex.between("a1", "a1"));
    }
}
