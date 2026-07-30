package com.flora.ai.memory;

/** 记忆评分（新鲜度 + 相关性 + 重要性）。 */
public record MemoryScore(double recency, double relevance, double importance) {

    /** 综合评分。 */
    public double total(double recencyWeight, double relevanceWeight) {
        double importanceWeight = 1.0 - recencyWeight - relevanceWeight;
        return recency * recencyWeight + relevance * relevanceWeight + importance * importanceWeight;
    }
}
