package com.flora.ai.chat;

/** LLM 发出的工具调用请求。 */
public record ToolCall(String id, String name, String arguments) {}
