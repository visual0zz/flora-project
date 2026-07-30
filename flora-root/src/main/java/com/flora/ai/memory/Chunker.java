package com.flora.ai.memory;

import java.util.*;

/**
 * 文本分块算法。
 * <p>纯算法：按 token 估算、句子、段落切分文本。</p>
 */
public class Chunker {

    private Chunker() {}

    /** 按目标块大小切分（基于字符数估算）。 */
    public static List<String> splitBySize(String text, int chunkSize, int overlap) {
        if (text == null || text.isEmpty()) return List.of();
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            start += chunkSize - overlap;
        }
        return chunks;
    }

    /** 按段落切分（以连续换行为界）。 */
    public static List<String> splitByParagraph(String text) {
        if (text == null || text.isEmpty()) return List.of();
        String[] parts = text.split("\\n\\s*\\n");
        return Arrays.stream(parts).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /** 按句子切分（以句号/问号/感叹号为界）。 */
    public static List<String> splitBySentence(String text) {
        if (text == null || text.isEmpty()) return List.of();
        String[] parts = text.split("(?<=[。！？.!?])\\s*");
        return Arrays.stream(parts).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /** 递归切分到所有块不超过 maxSize。 */
    public static List<String> recursiveSplit(String text, int maxSize) {
        List<String> result = new ArrayList<>();
        recursiveSplit(text, maxSize, result);
        return result;
    }

    private static void recursiveSplit(String text, int maxSize, List<String> result) {
        if (text.length() <= maxSize) {
            result.add(text);
            return;
        }
        // 优先按段落切
        List<String> paragraphs = splitByParagraph(text);
        if (paragraphs.size() > 1) {
            for (String p : paragraphs) {
                recursiveSplit(p, maxSize, result);
            }
            return;
        }
        // 按句子切
        List<String> sentences = splitBySentence(text);
        if (sentences.size() > 1) {
            for (String s : sentences) {
                recursiveSplit(s, maxSize, result);
            }
            return;
        }
        // 强行按大小切
        result.addAll(splitBySize(text, maxSize, 0));
    }
}
