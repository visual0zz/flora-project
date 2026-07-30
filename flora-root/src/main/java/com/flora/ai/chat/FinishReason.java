package com.flora.ai.chat;

/** LLM 回应终止原因。 */
public enum FinishReason {
    STOP,
    LENGTH,
    TOOL_CALL,
    CONTENT_FILTER,
    ERROR
}
