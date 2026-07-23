
package com.flora.fast.container.map;

import com.flora.fast.container.consumer.*;
import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;


class FastHashMapMapApiTest {

    
    
    

    @Test
    void int2int_getObject() {
        var map = new Int2IntFastHashMap();
        map.put(1, 100);
        map.put(2, 200);

        assertEquals(Integer.valueOf(100), map.get(1));
        assertEquals(Integer.valueOf(200), map.get(2));
        assertNull(map.get((Object) 99));           
        assertNull(map.get("wrong type"));           
    }

    @Test
    void int2int_containsKeyObject() {
        var map = new Int2IntFastHashMap();
        map.put(1, 100);
        map.put(2, 200);

        assertTrue(map.containsKey((Object) 1));
        assertTrue(map.containsKey(1));
        assertFalse(map.containsKey((Object) 99));
        assertFalse(map.containsKey("wrong type"));  
    }

    @Test
    void int2int_putBoxed() {
        var map = new Int2IntFastHashMap();
        assertNull(map.put(Integer.valueOf(1), Integer.valueOf(100)));
        assertEquals(Integer.valueOf(100), map.put(Integer.valueOf(1), Integer.valueOf(999)));
        assertEquals(999, map.get(1));
    }

    @Test
    void int2int_removeObject() {
        var map = new Int2IntFastHashMap();
        map.put(1, 100);
        map.put(2, 200);

        assertEquals(Integer.valueOf(100), map.remove((Object) 1));
        assertNull(map.get((Object) 1));
        assertEquals(1, map.size());

        assertNull(map.remove((Object) 99));        
        assertNull(map.remove("wrong type"));        
    }

    @Test
    void int2int_entrySet_iterator() {
        var map = new Int2IntFastHashMap();
        map.put(1, 100);
        map.put(2, 200);
        map.put(3, 300);

        var entries = map.entrySet();
        assertEquals(3, entries.size());

        int count = 0;
        int sum = 0;
        for (Map.Entry<Integer, Integer> e : entries) {
            count++;
            sum += e.getValue();
        }
        assertEquals(3, count);
        assertEquals(600, sum);
    }

    @Test
    void int2int_entrySet_empty() {
        var map = new Int2IntFastHashMap();
        assertTrue(map.entrySet().isEmpty());
        assertEquals(0, map.entrySet().size());
        assertFalse(map.entrySet().iterator().hasNext());
    }

    @Test
    void int2int_entrySet_iterator_exhaustion() {
        var map = new Int2IntFastHashMap();
        map.put(1, 100);
        Iterator<Map.Entry<Integer, Integer>> it = map.entrySet().iterator();
        assertTrue(it.hasNext());
        it.next();
        assertFalse(it.hasNext());
        assertThrows(NoSuchElementException.class, it::next);
    }

    @Test
    void int2int_entrySet_contains() {
        var map = new Int2IntFastHashMap();
        map.put(1, 100);
        map.put(2, 200);

        assertTrue(map.entrySet().contains(Map.entry(1, 100)));
        assertTrue(map.entrySet().contains(Map.entry(2, 200)));
        assertFalse(map.entrySet().contains(Map.entry(1, 999)));   
        assertFalse(map.entrySet().contains(Map.entry(99, 100)));  
        assertFalse(map.entrySet().contains("not an entry"));       
    }

    @Test
    void int2int_entrySet_remove() {
        var map = new Int2IntFastHashMap();
        map.put(1, 100);
        map.put(2, 200);
        map.put(3, 300);

        assertTrue(map.entrySet().remove(Map.entry(1, 100)));
        assertEquals(2, map.size());
        assertNull(map.get((Object) 1));

        
        assertFalse(map.entrySet().remove(Map.entry(1, 100)));
        assertFalse(map.entrySet().remove("not an entry"));
    }

    @Test
    void int2int_forEach() {
        var map = new Int2IntFastHashMap();
        map.put(1, 10);
        map.put(2, 20);
        map.put(3, 30);

        int[] sum = {0};
        Int2IntConsumer fn = (k, v) -> sum[0] += k + v;
        map.forEach(fn);
        assertEquals((1 + 10) + (2 + 20) + (3 + 30), sum[0]);
    }

    @Test
    void int2int_zeroKey() {
        var map = new Int2IntFastHashMap();
        map.put(0, 999);   

        assertTrue(map.entrySet().contains(Map.entry(0, 999)));
        assertEquals(Integer.valueOf(999), map.get(0));

        
        int[] count = {0};
        map.forEach((Int2IntConsumer) (k, v) -> count[0]++);
        assertEquals(1, count[0]);

        
        assertEquals(1, map.entrySet().size());
        var entry = map.entrySet().iterator().next();
        assertEquals(Integer.valueOf(0), entry.getKey());
        assertEquals(Integer.valueOf(999), entry.getValue());
    }

    
    
    

    @Test
    void object2object_getObject() {
        var map = new Object2ObjectFastHashMap();
        map.put("a", "alpha");
        map.put("b", "beta");

        assertEquals("alpha", map.get("a"));
        assertEquals("beta", map.get("b"));
        assertNull(map.get("missing"));
    }

    @Test
    void object2object_putBoxed() {
        var map = new Object2ObjectFastHashMap();
        assertNull(map.put("k1", "v1"));
        assertEquals("v1", map.put("k1", "v2"));
        assertEquals("v2", map.get("k1"));
    }

    @Test
    void object2object_removeObject() {
        var map = new Object2ObjectFastHashMap();
        map.put("x", "y");
        assertEquals("y", map.remove("x"));
        assertNull(map.get("x"));
        assertEquals(0, map.size());
    }

    @Test
    void object2object_nullKey() {
        var map = new Object2ObjectFastHashMap();
        map.put(null, "nullval");

        assertEquals("nullval", map.get(null));
        assertTrue(map.containsKey(null));
        assertTrue(map.entrySet().contains(new AbstractMap.SimpleEntry<>(null, "nullval")));

        
        int[] count = {0};
        map.forEach((Object2ObjectConsumer) (k, v) -> count[0]++);
        assertEquals(1, count[0]);
    }

    @Test
    void object2object_entrySet() {
        var map = new Object2ObjectFastHashMap();
        map.put("a", 1);
        map.put("b", 2);
        map.put("c", 3);

        var entries = map.entrySet();
        assertEquals(3, entries.size());

        assertTrue(entries.contains(Map.entry("a", 1)));
        assertFalse(entries.contains(Map.entry("a", 999)));
        assertFalse(entries.contains("garbage"));

        assertTrue(entries.remove(Map.entry("b", 2)));
        assertEquals(2, map.size());
        assertNull(map.get("b"));
    }

    
    
    

    @Test
    void object2long_getObject() {
        var map = new Object2LongFastHashMap();
        map.putRaw("a", 100L);
        map.putRaw("b", 200L);

        assertEquals(Long.valueOf(100), map.get("a"));
        assertEquals(Long.valueOf(200), map.get("b"));
        assertNull(map.get("missing"));
    }

    @Test
    void object2long_containsKeyObject() {
        var map = new Object2LongFastHashMap();
        map.putRaw("a", 1L);

        assertTrue(map.containsKey("a"));
        assertFalse(map.containsKey("b"));
        assertFalse(map.containsKey(42));   
    }

    @Test
    void object2long_putBoxed() {
        var map = new Object2LongFastHashMap();
        assertNull(map.put("k", Long.valueOf(100)));
        assertEquals(Long.valueOf(100), map.put("k", Long.valueOf(999)));
        assertEquals(999L, map.getRaw("k"));
    }

    @Test
    void object2long_removeObject() {
        var map = new Object2LongFastHashMap();
        map.putRaw("x", 500L);

        assertEquals(Long.valueOf(500), map.remove("x"));
        assertEquals(0, map.size());
        assertEquals(-1L, map.getRaw("x"));

        assertNull(map.remove("missing"));
    }

    @Test
    void object2long_entrySet() {
        var map = new Object2LongFastHashMap();
        map.putRaw("a", 10L);
        map.putRaw("b", 20L);

        var entries = map.entrySet();
        assertEquals(2, entries.size());

        assertTrue(entries.contains(Map.entry("a", 10L)));
        assertFalse(entries.contains(Map.entry("a", 99L)));

        assertTrue(entries.remove(Map.entry("a", 10L)));
        assertEquals(1, map.size());

        var it = entries.iterator();
        assertTrue(it.hasNext());
        var e = it.next();
        assertEquals("b", e.getKey());
        assertEquals(Long.valueOf(20), e.getValue());
    }

    @Test
    void object2long_forEach() {
        var map = new Object2LongFastHashMap();
        map.putRaw("one", 1L);
        map.putRaw("two", 2L);

        long[] sum = {0};
        map.forEach((Object2LongConsumer) (k, v) -> sum[0] += v);
        assertEquals(3L, sum[0]);
    }

    
    
    

    @Test
    void byte2byte_entrySet_hashMode() {
        var map = new Byte2ByteFastHashMap();
        map.put((byte) 10, (byte) 100);
        map.put((byte) 20, (byte) 200);

        var entries = map.entrySet();
        assertEquals(2, entries.size());

        assertTrue(entries.contains(Map.entry((byte) 10, (byte) 100)));
        assertTrue(entries.contains(Map.entry((byte) 20, (byte) 200)));

        
        int[] count = {0};
        map.forEach((Byte2ByteConsumer) (k, v) -> count[0]++);
        assertEquals(2, count[0]);
    }

    @Test
    void byte2byte_entrySet_directMode() {
        
        var map = new Byte2ByteFastHashMap(256, 0.75f);
        for (int i = 0; i < 256; i++) {
            map.put((byte) i, (byte) (i * 2));
        }

        
        assertEquals(256, map.size());

        var entries = map.entrySet();
        assertEquals(256, entries.size());
        assertTrue(entries.contains(Map.entry((byte) 0, (byte) 0)));
        assertTrue(entries.contains(Map.entry((byte) 255, (byte) (255 * 2))));
        assertFalse(entries.contains(Map.entry((byte) 0, (byte) 99)));

        
        assertTrue(entries.remove(Map.entry((byte) 0, (byte) 0)));
        assertEquals(255, map.size());

        
        int[] count = {0};
        map.forEach((Byte2ByteConsumer) (k, v) -> count[0]++);
        assertEquals(255, count[0]);
    }

    @Test
    void byte2byte_entrySet_modeTransition() {
        
        var map = new Byte2ByteFastHashMap();

        map.put((byte) 1, (byte) 10);
        map.put((byte) 2, (byte) 20);
        assertEquals(2, map.entrySet().size());

        
        for (int i = 0; i < 256; i++) {
            map.put((byte) i, (byte) i);
        }
        assertEquals(256, map.entrySet().size());
    }

    @Test
    void byte2byte_zeroKey() {
        var map = new Byte2ByteFastHashMap();
        map.put((byte) 0, (byte) 99);   

        assertTrue(map.entrySet().contains(Map.entry((byte) 0, (byte) 99)));

        int[] count = {0};
        map.forEach((Byte2ByteConsumer) (k, v) -> count[0]++);
        assertEquals(1, count[0]);
    }

    @Test
    void byte2byte_getObject() {
        var map = new Byte2ByteFastHashMap();
        map.put((byte) 5, (byte) 50);

        assertEquals(Byte.valueOf((byte) 50), map.get((byte) 5));
        assertNull(map.get((Object) (byte) 99));
        assertNull(map.get("wrong type"));
    }

    @Test
    void byte2byte_removeObject() {
        var map = new Byte2ByteFastHashMap();
        map.put((byte) 7, (byte) 70);

        assertEquals(Byte.valueOf((byte) 70), map.remove((byte) 7));
        assertEquals(0, map.size());
        assertNull(map.remove((Object) (byte) 99));
        assertNull(map.remove("wrong type"));
    }

    
    
    

    @Test
    void allTypes_defaultReturnAndMapApi() {
        
        var map = new Int2IntFastHashMap();
        map.defaultReturnValue(-42);
        assertEquals(Integer.valueOf(-42), map.get(999));

        
        var omap = new Object2ObjectFastHashMap();
        omap.defaultReturnValue("NOT_FOUND");
        assertNull(omap.get("missing"));
    }

    @Test
    void int2long_boxedTypes() {
        var map = new Int2LongFastHashMap();
        map.put(1, 100L);

        assertEquals(Long.valueOf(100), map.get((Object) 1));
        assertEquals(Long.valueOf(100), map.put(1, 200L));
        assertEquals(Long.valueOf(200), map.remove((Object) 1));
    }

    @Test
    void long2double_boxedTypes() {
        var map = new Long2DoubleFastHashMap();
        map.put(10L, 3.14);
        assertEquals(Double.valueOf(3.14), map.get((Object) 10L));
    }

    @Test
    void char2float_boxedTypes() {
        var map = new Char2FloatFastHashMap();
        map.put('A', 1.5f);
        assertEquals(Float.valueOf(1.5f), map.get((Object) 'A'));
    }

    @Test
    void short2byte_boxedTypes() {
        var map = new Short2ByteFastHashMap();
        map.put((short) 42, (byte) 7);
        assertEquals(Byte.valueOf((byte) 7), map.get((Object) (short) 42));
    }

    @Test
    void float2int_containsKeyWrongType() {
        var map = new Float2IntFastHashMap();
        map.put(1.0f, 100);
        assertFalse(map.containsKey("not a float"));
    }

    @Test
    void double2long_removeWrongType() {
        var map = new Double2LongFastHashMap();
        map.put(1.5, 100L);
        assertNull(map.remove("not a double"));
    }
}
