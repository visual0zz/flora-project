package com.flora.ai.mcp;

import java.util.Map;

/** MCP 工具定义。 */
public record McpTool(String name, String description, Map<String, Object> inputSchema) {}
