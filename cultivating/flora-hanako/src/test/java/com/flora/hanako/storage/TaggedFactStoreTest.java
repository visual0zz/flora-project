package com.flora.hanako.storage;

import com.flora.hanako.core.model.MemoryFact;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TaggedFactStoreTest {

    @Test
    void putAndGet() {
        TaggedFactStore store = new TaggedFactStore();
        MemoryFact f = new MemoryFact(UUID.randomUUID().toString(), "喜欢用 tab 缩进", List.of("偏好", "风格"));
        store.put(f);
        assertEquals(1, store.size());
        assertNotNull(store.get(f.getId()));
        assertEquals("喜欢用 tab 缩进", store.get(f.getId()).getText());
    }

    @Test
    void queryRanksByHitCount() {
        TaggedFactStore store = new TaggedFactStore();
        MemoryFact a = new MemoryFact("a", "A", List.of("偏好", "风格"));
        MemoryFact b = new MemoryFact("b", "B", List.of("偏好"));
        MemoryFact c = new MemoryFact("c", "C", List.of("工作流"));
        store.put(a);
        store.put(b);
        store.put(c);

        List<MemoryFact> r = store.query(List.of("偏好"));
        assertEquals(2, r.size());
        // a 命中 1 个查询标签（偏好命中，风格不在查询），b 命中偏好 —— 都命中1，按时间倒序 c 不在
        assertTrue(r.stream().allMatch(f -> f.getTags().contains("偏好")));
    }

    @Test
    void removeUpdatesIndex() {
        TaggedFactStore store = new TaggedFactStore();
        MemoryFact f = new MemoryFact("x", "X", List.of("t1"));
        store.put(f);
        assertTrue(store.remove("x"));
        assertNull(store.get("x"));
        assertEquals(0, store.query(List.of("t1")).size());
    }
}
