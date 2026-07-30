package com.flora.ai.chat;

/** LLM 调用 token 消耗统计。 */
public record TokenUsage(int promptTokens, int completionTokens, int cachedTokens) {

    public TokenUsage(int promptTokens, int completionTokens) {
        this(promptTokens, completionTokens, 0);
    }

    public int total() { return promptTokens + completionTokens; }
}
