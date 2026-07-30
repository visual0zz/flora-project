package com.flora.ai.chat;

/** LLM 调用参数。 */
public record LlmOptions(
    Double temperature,
    Integer maxTokens,
    Double topP,
    Integer seed,
    Boolean stream
) {
    public LlmOptions() { this(null, null, null, null, null); }
    public LlmOptions(Double temperature, Integer maxTokens) {
        this(temperature, maxTokens, null, null, null);
    }
}
