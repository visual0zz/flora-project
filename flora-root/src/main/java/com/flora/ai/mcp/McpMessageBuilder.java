package com.flora.ai.mcp;

import java.util.*;

/**
 * MCP JSON-RPC 消息构建器。
 * <p>纯算法，构建请求/响应/通知的 Map 结构。</p>
 */
public class McpMessageBuilder {

    private McpMessageBuilder() {}

    private static int seq = 0;

    /** 构建 JSON-RPC 请求。 */
    public static Map<String, Object> buildRequest(String method, Map<String, Object> params) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("jsonrpc", "2.0");
        msg.put("id", ++seq);
        msg.put("method", method);
        if (params != null && !params.isEmpty()) {
            msg.put("params", new LinkedHashMap<>(params));
        }
        return msg;
    }

    /** 构建 JSON-RPC 响应。 */
    public static Map<String, Object> buildResponse(int id, Object result) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("jsonrpc", "2.0");
        msg.put("id", id);
        msg.put("result", result);
        return msg;
    }

    /** 构建 JSON-RPC 错误响应。 */
    public static Map<String, Object> buildError(int id, int code, String message, Object data) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("jsonrpc", "2.0");
        msg.put("id", id);
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        if (data != null) error.put("data", data);
        msg.put("error", error);
        return msg;
    }

    /** 构建 JSON-RPC 通知（无 id）。 */
    public static Map<String, Object> buildNotification(String method, Map<String, Object> params) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("jsonrpc", "2.0");
        msg.put("method", method);
        if (params != null && !params.isEmpty()) {
            msg.put("params", new LinkedHashMap<>(params));
        }
        return msg;
    }

    /** 构建 MCP 初始化请求。 */
    public static Map<String, Object> buildInitialize(McpVersion version) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("protocolVersion", version.protocolVersion());
        params.put("capabilities", Map.of(
            "tools", version.clientCapabilities().tools(),
            "resources", version.clientCapabilities().resources(),
            "prompts", version.clientCapabilities().prompts()
        ));
        params.put("clientInfo", Map.of("name", "flora-ai", "version", "1.0"));
        return buildRequest("initialize", params);
    }

    /** 构建 tools/list 请求。 */
    public static Map<String, Object> buildListTools() {
        return buildRequest("tools/list", null);
    }

    /** 构建 tools/call 请求。 */
    public static Map<String, Object> buildCallTool(String name, Map<String, Object> args) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", name);
        params.put("arguments", args != null ? new LinkedHashMap<>(args) : new LinkedHashMap<>());
        return buildRequest("tools/call", params);
    }
}
