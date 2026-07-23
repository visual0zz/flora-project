package com.flora.fast.container.map;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class EntrySetDiagTest {
    @Test
    void diagInt2IntEntrySetRemove() {
        var map = new Int2IntFastHashMap();
        map.put(1, 100);
        map.put(2, 200);
        map.put(3, 300);
        System.out.println("size before remove: " + map.size());

        var entry = Map.entry(1, 100);
        System.out.println("entry class: " + entry.getClass().getName());
        System.out.println("entry key type: " + entry.getKey().getClass().getName());

        var es = map.entrySet();
        System.out.println("entrySet class: " + es.getClass().getName());
        boolean removed = es.remove(entry);
        System.out.println("removed: " + removed);
        System.out.println("size after remove: " + map.size());

        assertEquals(2, map.size());
        System.out.println("containsKey(1): " + map.containsKey(1));
        System.out.println("containsKey(2): " + map.containsKey(2));
        System.out.println("containsKey(3): " + map.containsKey(3));
    }
}
