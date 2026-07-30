package com.flora.ai.chat;

import java.util.List;

/** 对话消息体。 */
public record ChatMessage(ChatRole role, String content, List<ToolCall> toolCalls) {

    public ChatMessage(ChatRole role, String content) {
        this(role, content, List.of());
    }

    public static ChatMessage system(String content) {
        return new ChatMessage(ChatRole.SYSTEM, content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(ChatRole.USER, content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(ChatRole.ASSISTANT, content);
    }

    public static ChatMessage assistant(List<ToolCall> toolCalls) {
        return new ChatMessage(ChatRole.ASSISTANT, "", toolCalls);
    }

    public static ChatMessage tool(String toolCallId, String content) {
        return new ChatMessage(ChatRole.TOOL, content);
    }
}
