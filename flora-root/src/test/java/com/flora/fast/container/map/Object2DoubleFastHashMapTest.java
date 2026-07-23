
package com.flora.fast.container.map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Object2DoubleFastHashMapTest {

    private static Object k(int v) { return (Object) v; }
    private static double v(int val) { return (double) val; }

    @Test
    void invalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> new Object2DoubleFastHashMap(0));
        assertThrows(IllegalArgumentException.class, () -> new Object2DoubleFastHashMap(-1));
        assertThrows(IllegalArgumentException.class, () -> new Object2DoubleFastHashMap(16, 0f));
        assertThrows(IllegalArgumentException.class, () -> new Object2DoubleFastHashMap(16, 1f));
        assertThrows(IllegalArgumentException.class, () -> new Object2DoubleFastHashMap(16, -0.1f));
        assertThrows(IllegalArgumentException.class, () -> new Object2DoubleFastHashMap(16, 1.5f));
    }

    @Test
    void putAndGet() {
        var map = new Object2DoubleFastHashMap();
        assertEquals(0.0d, map.putRaw(k(2), v(200)));
        assertEquals(0.0d, map.putRaw(k(1), v(100)));
        assertEquals(0.0d, map.putRaw(k(3), v(300)));
        assertTrue(map.containsKey(k(3)));
        assertTrue(map.containsKey(k(1)));
        assertTrue(map.containsKey(k(2)));
        assertFalse(map.containsKey(k(4)));
        assertFalse(map.containsKey(k(5)));
        assertFalse(map.containsKey(k(6)));
        assertEquals(v(100), map.getRaw(k(1)));
        assertEquals(v(200), map.getRaw(k(2)));
        assertEquals(v(300), map.getRaw(k(3)));

        assertEquals(0.0d, map.putRaw(k(4), v(100)));
        assertEquals(v(100), map.putRaw(k(4), v(999)));
        assertEquals(v(999), map.getRaw(k(4)));

        map.putRaw(k(5), v(100));
        assertEquals(v(100), map.getOrDefault(k(5), v(-2)));
        assertEquals(v(-2), map.getOrDefault(k(99), v(-2)));

        assertEquals(0.0d, map.getRaw(k(42)));
        map.defaultReturnValue(0.0d);
        assertEquals(0.0d, map.getRaw(k(42)));
    }

    @Test
    void remove() {
        var map = new Object2DoubleFastHashMap();
        map.putRaw(k(1), v(100));
        map.putRaw(k(2), v(200));

        assertEquals(v(100), map.removeRaw(k(1)));
        assertEquals(0.0d, map.getRaw(k(1)));
        assertEquals(v(200), map.getRaw(k(2)));
        assertEquals(1, map.size());

        assertEquals(0.0d, map.removeRaw(k(99)));

        map.defaultReturnValue(v(-2));
        assertEquals(v(-2), map.removeRaw(k(999)));
    }

    @Test
    void clear() {
        var map = new Object2DoubleFastHashMap();
        map.putRaw(k(1), v(10));
        map.putRaw(k(2), v(20));
        map.putRaw(k(3), v(30));
        assertEquals(3, map.size());
        assertFalse(map.isEmpty());

        map.clear();
        assertEquals(0, map.size());
        assertTrue(map.isEmpty());
        assertEquals(0.0d, map.getRaw(k(1)));
        assertEquals(0.0d, map.getRaw(k(2)));
        assertEquals(0.0d, map.getRaw(k(3)));
    }

    @Test
    void sizeAndIsEmpty() {
        var map = new Object2DoubleFastHashMap();
        assertTrue(map.isEmpty());
        assertEquals(0, map.size());

        map.putRaw(k(1), v(1));
        assertFalse(map.isEmpty());
        assertEquals(1, map.size());

        map.putRaw(k(2), v(2));
        assertEquals(2, map.size());

        map.removeRaw(k(1));
        assertEquals(1, map.size());
    }

    @Test
    void defaultReturnValue() {
        var map = new Object2DoubleFastHashMap();
        assertEquals(0.0d, map.defaultReturnValue());

        map.defaultReturnValue(0.0d);
        assertEquals(0.0d, map.defaultReturnValue());
        assertEquals(0.0d, map.getRaw(k(999)));
    }

    @Test
    void shouldRehashWhenExceedingCapacity() {
        var map = new Object2DoubleFastHashMap(4, 0.75f);
        for (int i = 1; i <= 1000; i++) {
            assertEquals(0.0d, map.putRaw(k(i), v(i * 10)));
        }
        assertEquals(1000, map.size());
        for (int i = 1; i <= 1000; i++) {
            assertEquals(v(i * 10), map.getRaw(k(i)));
        }
    }

    @Test
    void shouldReuseDeletedSlots() {
        var map = new Object2DoubleFastHashMap();
        for (int i = 0; i < 20; i++) {
            map.putRaw(k(i), v(i));
        }
        for (int i = 0; i < 10; i++) {
            map.removeRaw(k(i));
        }
        for (int i = 20; i < 30; i++) {
            assertEquals(0.0d, map.putRaw(k(i), v(i)));
        }
        assertEquals(20, map.size());
        for (int i = 0; i < 10; i++) {
            assertEquals(0.0d, map.getRaw(k(i)));
        }
        for (int i = 10; i < 30; i++) {
            assertEquals(v(i), map.getRaw(k(i)));
        }
    }

    @Test
    void shouldHandleCollisions() {
        var map = new Object2DoubleFastHashMap();
        for (int i = 0; i < 1000; i++) {
            assertEquals(0.0d, map.putRaw(k(i), v(i * 2)));
        }
        for (int i = 0; i < 1000; i++) {
            assertEquals(v(i * 2), map.getRaw(k(i)));
        }
    }

    @Test
    void shouldCorrectlyIncreaseSize() {
        var map = new Object2DoubleFastHashMap();
        for (int i = 0; i < 1000; i++) {
            map.putRaw(k(i), v(i));
            assertEquals(i + 1, map.size());
        }
    }

    @Test
    void shouldCorrectlyDecreaseSize() {
        var map = new Object2DoubleFastHashMap();
        for (int i = 0; i < 100; i++) {
            map.putRaw(k(i), v(i));
        }
        for (int i = 0; i < 100; i++) {
            map.removeRaw(k(i));
            assertEquals(100 - i - 1, map.size());
        }
        assertTrue(map.isEmpty());
    }
}
