package com.flora.ai.mcp;

/** MCP 端点描述。 */
public record McpEndpoint(String serverName, McpTransportType transportType, McpCapabilitySet capabilities) {}
