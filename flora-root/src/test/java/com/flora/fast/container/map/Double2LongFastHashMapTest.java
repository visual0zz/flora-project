
package com.flora.fast.container.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Double2LongFastHashMapTest {

    private static double k(int v) { return (double) v; }
    private static long v(int val) { return (long) val; }

    @Test
    void invalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> new Double2LongFastHashMap(0));
        assertThrows(IllegalArgumentException.class, () -> new Double2LongFastHashMap(-1));
        assertThrows(IllegalArgumentException.class, () -> new Double2LongFastHashMap(16, 0f));
        assertThrows(IllegalArgumentException.class, () -> new Double2LongFastHashMap(16, 1f));
        assertThrows(IllegalArgumentException.class, () -> new Double2LongFastHashMap(16, -0.1f));
        assertThrows(IllegalArgumentException.class, () -> new Double2LongFastHashMap(16, 1.5f));
    }

    @Test
    void putAndGet() {
        var map = new Double2LongFastHashMap();
        assertEquals(-1L, map.put(k(2), v(200)));
        assertEquals(-1L, map.put(k(1), v(100)));
        assertEquals(-1L, map.put(k(3), v(300)));
        assertTrue(map.containsKey(k(3)));
        assertTrue(map.containsKey(k(1)));
        assertTrue(map.containsKey(k(2)));
        assertFalse(map.containsKey(k(4)));
        assertFalse(map.containsKey(k(5)));
        assertFalse(map.containsKey(k(6)));
        assertEquals(v(100), map.get(k(1)));
        assertEquals(v(200), map.get(k(2)));
        assertEquals(v(300), map.get(k(3)));

        assertEquals(-1L, map.put(k(4), v(100)));
        assertEquals(v(100), map.put(k(4), v(999)));
        assertEquals(v(999), map.get(k(4)));

        map.put(k(5), v(100));
        assertEquals(v(100), map.getOrDefault(k(5), v(-2)));
        assertEquals(v(-2), map.getOrDefault(k(99), v(-2)));

        assertEquals(-1L, map.get(k(42)));
        map.defaultReturnValue(-1L);
        assertEquals(-1L, map.get(k(42)));
    }

    @Test
    void remove() {
        var map = new Double2LongFastHashMap();
        map.put(k(1), v(100));
        map.put(k(2), v(200));

        assertEquals(v(100), map.remove(k(1)));
        assertEquals(-1L, map.get(k(1)));
        assertEquals(v(200), map.get(k(2)));
        assertEquals(1, map.size());

        assertEquals(-1L, map.remove(k(99)));

        map.defaultReturnValue(v(-2));
        assertEquals(v(-2), map.remove(k(999)));
    }

    @Test
    void clear() {
        var map = new Double2LongFastHashMap();
        map.put(k(1), v(10));
        map.put(k(2), v(20));
        map.put(k(3), v(30));
        assertEquals(3, map.size());
        assertFalse(map.isEmpty());

        map.clear();
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
        assertEquals(-1L, map.get(k(1)));
        assertEquals(-1L, map.get(k(2)));
        assertEquals(-1L, map.get(k(3)));
    }

    @Test
    void sizeAndIsEmpty() {
        var map = new Double2LongFastHashMap();
        assertTrue(map.isEmpty());
        assertEquals(0, map.size());

        map.put(k(1), v(1));
        assertFalse(map.isEmpty());
        assertEquals(1, map.size());

        map.put(k(2), v(2));
        assertEquals(2, map.size());

        map.remove(k(1));
        assertEquals(1, map.size());
    }

    @Test
    void defaultReturnValue() {
        var map = new Double2LongFastHashMap();
        assertEquals(-1L, map.defaultReturnValue());

        map.defaultReturnValue(-1L);
        assertEquals(-1L, map.defaultReturnValue());
        assertEquals(-1L, map.get(k(999)));
    }

    @Test
    void shouldRehashWhenExceedingCapacity() {
        var map = new Double2LongFastHashMap(4, 0.75f);
        for (int i = 1; i <= 1000; i++) {
            assertEquals(-1L, map.put(k(i), v(i * 10)));
        }
        assertEquals(1000, map.size());
        for (int i = 1; i <= 1000; i++) {
            assertEquals(v(i * 10), map.get(k(i)));
        }
    }

    @Test
    void shouldReuseDeletedSlots() {
        var map = new Double2LongFastHashMap();
        for (int i = 0; i < 20; i++) {
            map.put(k(i), v(i));
        }
        for (int i = 0; i < 10; i++) {
            map.remove(k(i));
        }
        for (int i = 20; i < 30; i++) {
            assertEquals(-1L, map.put(k(i), v(i)));
        }
        assertEquals(20, map.size());
        for (int i = 0; i < 10; i++) {
            assertEquals(-1L, map.get(k(i)));
        }
        for (int i = 10; i < 30; i++) {
            assertEquals(v(i), map.get(k(i)));
        }
    }

    @Test
    void shouldHandleCollisions() {
        var map = new Double2LongFastHashMap();
        for (int i = 0; i < 1000; i++) {
            assertEquals(-1L, map.put(k(i), v(i * 2)));
        }
        for (int i = 0; i < 1000; i++) {
            assertEquals(v(i * 2), map.get(k(i)));
        }
    }

    @Test
    void shouldCorrectlyIncreaseSize() {
        var map = new Double2LongFastHashMap();
        for (int i = 0; i < 1000; i++) {
            map.put(k(i), v(i));
            assertEquals(i + 1, map.size());
        }
    }

    @Test
    void shouldCorrectlyDecreaseSize() {
        var map = new Double2LongFastHashMap();
        for (int i = 0; i < 100; i++) {
            map.put(k(i), v(i));
        }
        for (int i = 0; i < 100; i++) {
            map.remove(k(i));
            assertEquals(100 - i - 1, map.size());
        }
        assertTrue(map.isEmpty());
    }
}
