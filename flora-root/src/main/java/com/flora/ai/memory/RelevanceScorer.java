package com.flora.ai.memory;

import java.util.*;

/**
 * 文本相关性评分算法。
 * <p>纯算法：关键词匹配 + TF 加权。</p>
 */
public class RelevanceScorer {

    private RelevanceScorer() {}

    /** 基于关键词匹配的简单相关性评分（0.0 ~ 1.0）。 */
    public static double keywordMatch(String text, String query) {
        if (text == null || query == null || text.isEmpty()) return 0.0;
        String lowerText = text.toLowerCase();
        String[] queryWords = query.toLowerCase().split("\\s+");
        if (queryWords.length == 0) return 0.0;

        int hits = 0;
        for (String word : queryWords) {
            if (word.length() > 1 && lowerText.contains(word)) {
                hits++;
            }
        }
        return (double) hits / queryWords.length;
    }

    /** 基于 TF（词频）的评分。 */
    public static double tfScore(String text, String query) {
        if (text == null || query == null) return 0.0;
        String lowerText = text.toLowerCase();
        String[] queryWords = query.toLowerCase().split("\\s+");

        double score = 0;
        for (String word : queryWords) {
            if (word.length() <= 1) continue;
            int count = 0;
            int idx = 0;
            while ((idx = lowerText.indexOf(word, idx)) != -1) {
                count++;
                idx += word.length();
            }
            if (count > 0) {
                score += Math.log1p(count);
            }
        }
        return score > 0 ? Math.min(1.0, score / 10) : 0.0;
    }

    /** 余弦相似度（基于词袋模型）。 */
    public static double cosineSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null) return 0.0;
        Map<String, Integer> vec1 = termFreq(text1);
        Map<String, Integer> vec2 = termFreq(text2);
        if (vec1.isEmpty() || vec2.isEmpty()) return 0.0;

        double dot = 0, norm1 = 0, norm2 = 0;
        for (Map.Entry<String, Integer> e : vec1.entrySet()) {
            double freq = e.getValue();
            double freq2 = vec2.getOrDefault(e.getKey(), 0);
            dot += freq * freq2;
            norm1 += freq * freq;
        }
        for (Map.Entry<String, Integer> e : vec2.entrySet()) {
            norm2 += e.getValue() * e.getValue();
        }
        return dot / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    private static Map<String, Integer> termFreq(String text) {
        Map<String, Integer> freq = new java.util.LinkedHashMap<>();
        String[] words = text.toLowerCase().split("\\W+");
        for (String w : words) {
            if (w.length() > 1) freq.merge(w, 1, Integer::sum);
        }
        return freq;
    }
}
