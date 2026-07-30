package com.flora.ai.chat;

/** LLM 返回的候选结果之一。 */
public record ChatChoice(int index, ChatMessage message, FinishReason finishReason) {}
