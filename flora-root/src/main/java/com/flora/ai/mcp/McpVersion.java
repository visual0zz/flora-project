package com.flora.ai.mcp;

/** MCP 协议版本与能力协商数据。 */
public record McpVersion(String protocolVersion, McpCapabilitySet clientCapabilities, McpCapabilitySet serverCapabilities) {

    public static final String LATEST_PROTOCOL = "2025-03-26";

    public static McpVersion latest() {
        return new McpVersion(LATEST_PROTOCOL, McpCapabilitySet.all(), McpCapabilitySet.all());
    }
}
