package com.flora.ai.mcp;

/** MCP 服务器能力声明。 */
public record McpCapabilitySet(boolean tools, boolean resources, boolean prompts, boolean logging) {

    public static McpCapabilitySet all() {
        return new McpCapabilitySet(true, true, true, false);
    }
}
