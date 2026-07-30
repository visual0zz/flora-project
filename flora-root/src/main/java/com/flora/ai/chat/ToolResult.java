package com.flora.ai.chat;

/** 工具执行结果。 */
public record ToolResult(String toolCallId, String content, boolean error) {

    public static ToolResult ok(String toolCallId, String content) {
        return new ToolResult(toolCallId, content, false);
    }

    public static ToolResult fail(String toolCallId, String error) {
        return new ToolResult(toolCallId, error, true);
    }
}
