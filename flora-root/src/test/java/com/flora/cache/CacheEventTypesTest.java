package com.flora.cache;

import com.flora.cache.store.MemoryCache;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证写操作事件族：INSERT（新建）、UPDATE（更新）、TOUCH（刷新 TTL）
 * 以及聚合事件 MUTATE（任意写）。
 * 每次具体写都「具体事件 + MUTATE」一并触发，与失效侧 INVALIDATE 模式对称。
 */
class CacheEventTypesTest {

    @Test
    void putNewFiresInsertAndMutate() {
        MemoryCache<Integer, Integer> c = new MemoryCache<>();
        List<CacheEventType> specific = new ArrayList<>();
        List<CacheEventType> mutate = new ArrayList<>();
        c.addListener(CacheEventType.INSERT, (t, k, v) -> specific.add(t));
        c.addListener(CacheEventType.MUTATE, (t, k, v) -> mutate.add(t));

        c.put(1, 10);

        assertEquals(List.of(CacheEventType.INSERT), specific);
        assertEquals(List.of(CacheEventType.MUTATE), mutate);
    }

    @Test
    void putExistingFiresUpdateAndMutate() {
        MemoryCache<Integer, Integer> c = new MemoryCache<>();
        c.put(1, 10);
        List<CacheEventType> specific = new ArrayList<>();
        List<CacheEventType> mutate = new ArrayList<>();
        c.addListener(CacheEventType.UPDATE, (t, k, v) -> specific.add(t));
        c.addListener(CacheEventType.MUTATE, (t, k, v) -> mutate.add(t));

        c.put(1, 20);

        assertEquals(List.of(CacheEventType.UPDATE), specific);
        assertEquals(List.of(CacheEventType.MUTATE), mutate);
    }

    @Test
    void setTtlFiresTouchAndMutate() {
        MemoryCache<Integer, Integer> c = new MemoryCache<>();
        c.put(1, 10);
        List<CacheEventType> specific = new ArrayList<>();
        List<CacheEventType> mutate = new ArrayList<>();
        c.addListener(CacheEventType.TOUCH, (t, k, v) -> specific.add(t));
        c.addListener(CacheEventType.MUTATE, (t, k, v) -> mutate.add(t));

        c.setTtl(1, Duration.ofMinutes(5));

        assertEquals(List.of(CacheEventType.TOUCH), specific);
        assertEquals(List.of(CacheEventType.MUTATE), mutate);
    }

    @Test
    void setTtlOnMissingKeyFiresNothing() {
        MemoryCache<Integer, Integer> c = new MemoryCache<>();
        List<CacheEventType> mutate = new ArrayList<>();
        c.addListener(CacheEventType.MUTATE, (t, k, v) -> mutate.add(t));
        c.addListener(CacheEventType.TOUCH, (t, k, v) -> mutate.add(t));

        c.setTtl(99, Duration.ofMinutes(5)); // key 不存在，不应触发任何写事件

        assertTrue(mutate.isEmpty());
    }

    @Test
    void mutateAggregatesAllWrites() {
        MemoryCache<Integer, Integer> c = new MemoryCache<>();
        // 先放一个已存在 key，避免后续 put 被当成新建
        c.put(1, 0);
        List<CacheEventType> mutate = new ArrayList<>();
        c.addListener(CacheEventType.MUTATE, (t, k, v) -> mutate.add(t));

        c.put(2, 20);            // 新建 → INSERT + MUTATE
        c.put(1, 1);            // 更新 → UPDATE + MUTATE
        c.setTtl(1, Duration.ofMinutes(1)); // 刷新 TTL → TOUCH + MUTATE

        assertEquals(3, mutate.size());
        assertTrue(mutate.stream().allMatch(CacheEventType.MUTATE::equals));
    }

    @Test
    void putWithDurationFiresCorrectSpecificEvent() {
        MemoryCache<Integer, Integer> c = new MemoryCache<>();
        List<CacheEventType> inserts = new ArrayList<>();
        List<CacheEventType> updates = new ArrayList<>();
        c.addListener(CacheEventType.INSERT, (t, k, v) -> inserts.add(t));
        c.addListener(CacheEventType.UPDATE, (t, k, v) -> updates.add(t));

        c.put(1, 10, Duration.ofMinutes(1)); // 新建
        c.put(1, 20, Duration.ofMinutes(2)); // 更新

        assertEquals(1, inserts.size());
        assertEquals(1, updates.size());
    }
}
