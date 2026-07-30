package com.flora.ai.memory;

import java.util.*;

/**
 * 记忆条目合并工具。
 * <p>纯算法：合并重复/相似的记忆条目。</p>
 */
public class MemoryMerger {

    private final double similarityThreshold;

    public MemoryMerger(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public MemoryMerger() {
        this(0.6);
    }

    /** 合并相似条目：保留最新的，合并内容。 */
    public List<MemoryEntry> merge(List<MemoryEntry> entries) {
        if (entries == null || entries.size() <= 1) return entries;

        List<MemoryEntry> result = new ArrayList<>();
        boolean[] merged = new boolean[entries.size()];

        for (int i = 0; i < entries.size(); i++) {
            if (merged[i]) continue;
            MemoryEntry base = entries.get(i);
            String mergedContent = base.content();
            long mergedTime = base.timestamp();
            double mergedScore = base.score();

            for (int j = i + 1; j < entries.size(); j++) {
                if (merged[j]) continue;
                MemoryEntry other = entries.get(j);
                double sim = RelevanceScorer.tfScore(base.content(), other.content());
                if (sim >= similarityThreshold) {
                    mergedContent = mergedContent + "\n---\n" + other.content();
                    mergedTime = Math.max(mergedTime, other.timestamp());
                    mergedScore = Math.max(mergedScore, other.score());
                    merged[j] = true;
                }
            }
            result.add(new MemoryEntry(base.id(), mergedContent, base.metadata(), mergedTime, mergedScore));
        }
        return result;
    }
}
