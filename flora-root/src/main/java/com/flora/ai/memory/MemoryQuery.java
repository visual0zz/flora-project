package com.flora.ai.memory;

/** 记忆查询参数。 */
public record MemoryQuery(
    String query,
    int limit,
    double scoreThreshold,
    double recencyWeight,
    double relevanceWeight
) {
    public MemoryQuery(String query, int limit) {
        this(query, limit, 0.0, 0.3, 0.7);
    }
}
