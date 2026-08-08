package com.flora.hanako.storage;

import com.flora.hanako.core.model.MemoryFact;
import com.flora.tag.ModuleEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 标签化事实存储：倒排索引 + 多标签命中数降序排序。
 * <p>复刻 openhanako {@code lib/memory/fact-store.js} 的记忆核心数据结构。
 * 检索时按「命中标签数」聚合排序（命中越多越靠前），是知识型应用的通用算法，
 * 与具体存储后端无关（{@link #facts} 为内存态，可外部插拔持久化）。</p>
 */
@ModuleEntry
public final class TaggedFactStore {

    /** 事实目录 id → fact。 */
    private final Map<String, MemoryFact> facts = new LinkedHashMap<>();
    /** 标签倒排索引 tag → 命中的 factId 集合。 */
    private final Map<String, java.util.Set<String>> index = new HashMap<>();

    /** 入库一条事实（建/更新倒排索引）。 */
    public synchronized void put(MemoryFact fact) {
        MemoryFact prev = facts.get(fact.getId());
        if (prev != null) {
            for (String t : prev.getTags()) {
                java.util.Set<String> set = index.get(t);
                if (set != null) {
                    set.remove(fact.getId());
                }
            }
        }
        facts.put(fact.getId(), fact);
        for (String tag : fact.getTags()) {
            index.computeIfAbsent(tag, k -> new java.util.LinkedHashSet<>()).add(fact.getId());
        }
    }

    /** 按 id 取事实。 */
    public synchronized MemoryFact get(String id) {
        return facts.get(id);
    }

    /** 全部事实（按入库顺序）。 */
    public synchronized List<MemoryFact> all() {
        return new ArrayList<>(facts.values());
    }

    /**
     * 按标签检索：返回命中全部查询标签中至少一个的事实，按命中标签数降序、时间倒序。
     * <p>对应 fact-store.js 的「GROUP BY + COUNT(DISTINCT) 按命中数降序」。</p>
     */
    public synchronized List<MemoryFact> query(List<String> queryTags) {
        if (queryTags == null || queryTags.isEmpty()) {
            return all().stream()
                    .sorted(Comparator.comparingLong(MemoryFact::getTime).reversed())
                    .collect(Collectors.toList());
        }
        Map<String, Integer> hitCount = new HashMap<>();
        for (String tag : queryTags) {
            java.util.Set<String> ids = index.get(tag);
            if (ids != null) {
                for (String id : ids) {
                    hitCount.merge(id, 1, Integer::sum);
                }
            }
        }
        List<MemoryFact> result = new ArrayList<>();
        for (Map.Entry<String, Integer> e : hitCount.entrySet()) {
            MemoryFact f = facts.get(e.getKey());
            if (f != null) {
                result.add(f);
            }
        }
        result.sort((a, b) -> {
            int ha = hitCount.getOrDefault(a.getId(), 0);
            int hb = hitCount.getOrDefault(b.getId(), 0);
            if (ha != hb) {
                return Integer.compare(hb, ha);
            }
            return Long.compare(b.getTime(), a.getTime());
        });
        return result;
    }

    /** 删除事实并更新索引。 */
    public synchronized boolean remove(String id) {
        MemoryFact removed = facts.remove(id);
        if (removed == null) {
            return false;
        }
        for (String t : removed.getTags()) {
            java.util.Set<String> set = index.get(t);
            if (set != null) {
                set.remove(id);
            }
        }
        return true;
    }

    /** 当前事实数量。 */
    public synchronized int size() {
        return facts.size();
    }
}
