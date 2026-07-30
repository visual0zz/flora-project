package com.flora.ai.memory;

import java.util.Map;

/** 记忆条目。 */
public record MemoryEntry(
    String id,
    String content,
    Map<String, String> metadata,
    long timestamp,
    double score
) {}
