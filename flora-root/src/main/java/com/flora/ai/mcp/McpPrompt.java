package com.flora.ai.mcp;

import java.util.List;

/** MCP Prompt 定义。 */
public record McpPrompt(String name, String description, List<McpPromptArgument> arguments) {

    public record McpPromptArgument(String name, String description, boolean required) {}
}
