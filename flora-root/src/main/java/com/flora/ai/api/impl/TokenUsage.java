package com.flora.ai.api.impl;

/**
 * token 用量统计。
 */
public record TokenUsage(int inputTokens, int outputTokens,
                         int cacheReadTokens, int cacheWriteTokens) {

    public static final TokenUsage ZERO = new TokenUsage(0, 0, 0, 0);
}
