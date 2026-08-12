package com.flora.root.ai.api;

import java.util.Map;

/**
 * 模型发起的工具调用（tool_calls 中的一项）。
 */
public record ToolCall(String id, String name, Map<String, Object> arguments) {

    public static ToolCall of(String id, String name, Map<String, Object> arguments) {
        return new ToolCall(id, name, arguments == null ? Map.of() : Map.copyOf(arguments));
    }
}
