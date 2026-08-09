package com.flora.ai.api.provider.protocol;

import com.flora.ai.api.ChatRequest;
import com.flora.ai.api.ChatResponse;
import com.flora.ai.api.ContentBlock;
import com.flora.ai.api.Message;
import com.flora.ai.api.InferenceConfig;
import com.flora.ai.api.TokenUsage;
import com.flora.ai.api.ToolCall;
import com.flora.ai.api.ToolSpec;
import com.flora.ai.api.impl.JsonHelper;
import com.flora.codec.json.JsonBuilder;
import com.flora.codec.json.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anthropic Messages API 协议翻译。
 * <p>差异点：{@code system} 为顶层参数、content 为块数组、{@code max_tokens} 必填、
 * Extended Thinking 用 {@code thinking} 块、SSE 事件类型为 {@code content_block_delta}。
 * 工具调用用 {@code tool_use}/{@code tool_result} 块；JSON 模式通过强制单工具
 * （forced tool_use）保证结构化输出。</p>
 */
public final class AnthropicProtocol {

    /** Anthropic API 版本头。 */
    public static final String API_VERSION = "2023-06-01";

    /** max_tokens 默认值（Anthropic 必填）。 */
    public static final int DEFAULT_MAX_TOKENS = 1024;

    /** JSON 模式强制工具名。 */
    public static final String FORCED_TOOL_NAME = "json_output";

    private AnthropicProtocol() {
    }

    /** 构建请求体 JSON。 */
    public static String buildRequest(ChatRequest req, String modelId, boolean stream) {
        return JsonBuilder.toJsonString(buildRequestMap(req, modelId, stream, null));
    }

    public static Map<String, Object> buildRequestMap(ChatRequest req, String modelId, boolean stream,
                                                      Map<String, Object> responseFormat) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelId);

        // 顶层 system（统一抽象中的 ChatRequest.system 字段）
        if (req.system() != null && !req.system().isBlank()) {
            body.put("system", req.system());
        }

        body.put("messages", buildMessages(req.messages()));

        // max_tokens 必填
        int maxTokens = DEFAULT_MAX_TOKENS;
        InferenceConfig c = req.config();
        if (c != null && c.maxTokens() != null) {
            maxTokens = c.maxTokens();
        }
        body.put("max_tokens", maxTokens);

        // 思考：adaptive / enabled+effort
        if (c != null && c.thinking() != null && c.thinking() != com.flora.ai.api.Thinking.OFF) {
            Map<String, Object> thinking = new LinkedHashMap<>();
            switch (c.thinking()) {
                case com.flora.ai.api.Thinking.AUTO -> thinking.put("type", "adaptive");
                case com.flora.ai.api.Thinking.LOW -> {
                    thinking.put("type", "enabled");
                    thinking.put("effort", "low");
                }
                case com.flora.ai.api.Thinking.MEDIUM -> {
                    thinking.put("type", "enabled");
                    thinking.put("effort", "medium");
                }
                case com.flora.ai.api.Thinking.HIGH, com.flora.ai.api.Thinking.MAX -> {
                    thinking.put("type", "enabled");
                    thinking.put("effort", "high");
                }
                default -> {
                }
            }
            body.put("thinking", thinking);
        }

        // 工具调用
        if (req.tools() != null && !req.tools().isEmpty()) {
            body.put("tools", buildTools(req.tools()));
        }
        // JSON 模式：强制单工具
        if (responseFormat != null && !responseFormat.isEmpty()) {
            body.put("tools", List.of(forcedJsonTool()));
            body.put("tool_choice", Map.of("type", "tool", "name", FORCED_TOOL_NAME));
        }

        if (stream) {
            body.put("stream", true);
        }
        return body;
    }

    private static Map<String, Object> forcedJsonTool() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", FORCED_TOOL_NAME);
        tool.put("description", "Return the answer as a JSON object.");
        tool.put("input_schema", schema);
        return tool;
    }

    /** 构建 messages 数组（含 tool_use / tool_result 块）。 */
    private static List<Object> buildMessages(List<Message> messages) {
        List<Object> list = new ArrayList<>();
        for (Message m : messages) {
            Map<String, Object> msg = new LinkedHashMap<>();
            if (m.role() == Message.Role.TOOL) {
                // 工具结果：role=user + tool_result 块（content 支持 text/image 块数组）
                Map<String, Object> tr = new LinkedHashMap<>();
                tr.put("type", "tool_result");
                tr.put("tool_use_id", m.toolCallId() == null ? "" : m.toolCallId());
                tr.put("content", buildToolResultContent(m));
                if (m.error()) {
                    tr.put("is_error", true);
                }
                msg.put("role", "user");
                msg.put("content", List.of(tr));
                list.add(msg);
                continue;
            }
            msg.put("role", m.role().name().toLowerCase());
            if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
                // assistant 带工具调用 -> tool_use 块
                List<Object> blocks = new ArrayList<>();
                for (ContentBlock b : m.content()) {
                    if (b instanceof ContentBlock.Text text) {
                        Map<String, Object> t = new LinkedHashMap<>();
                        t.put("type", "text");
                        t.put("text", text.text());
                        blocks.add(t);
                    }
                }
                for (ToolCall tc : m.toolCalls()) {
                    Map<String, Object> tu = new LinkedHashMap<>();
                    tu.put("type", "tool_use");
                    tu.put("id", tc.id());
                    tu.put("name", tc.name());
                    tu.put("input", tc.arguments() == null ? Map.of() : tc.arguments());
                    blocks.add(tu);
                }
                msg.put("content", blocks);
            } else {
                msg.put("content", buildContentBlocks(m.content()));
            }
            list.add(msg);
        }
        return list;
    }

    /**
     * 构建 tool_result 内容：纯文本单块 → 字符串（简洁），否则 → 内容块数组（无损，支持图片）。
     */
    private static Object buildToolResultContent(Message m) {
        if (m.content().size() == 1 && m.content().getFirst() instanceof ContentBlock.Text(String text)) {
            return text;
        }
        return buildContentBlocks(m.content());
    }

    private static List<Object> buildTools(List<ToolSpec> tools) {
        List<Object> list = new ArrayList<>();
        for (ToolSpec t : tools) {
            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("name", t.name());
            tool.put("description", t.description());
            tool.put("input_schema", t.parameters() == null ? Map.of() : t.parameters());
            list.add(tool);
        }
        return list;
    }

    /** 构建 content 块数组（text/image）。 */
    private static List<Object> buildContentBlocks(List<ContentBlock> blocks) {
        List<Object> list = new ArrayList<>();
        for (ContentBlock b : blocks) {
            switch (b) {
                case ContentBlock.Text text -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("type", "text");
                    m.put("text", text.text());
                    list.add(m);
                }
                case ContentBlock.Image img -> {
                    Map<String, Object> source = new LinkedHashMap<>();
                    source.put("type", "base64");
                    source.put("media_type", img.mediaType());
                    // dataUrl 可能是 "data:image/png;base64,xxx" 或纯 base64
                    source.put("data", stripDataUrlPrefix(img.dataUrl()));
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("type", "image");
                    m.put("source", source);
                    list.add(m);
                }
                default -> throw new IllegalArgumentException(
                        "Anthropic 不支持的内容块: " + b.getClass().getSimpleName());
            }
        }
        return list;
    }

    private static String stripDataUrlPrefix(String dataUrl) {
        int comma = dataUrl.indexOf(',');
        return comma >= 0 ? dataUrl.substring(comma + 1) : dataUrl;
    }

    /** 解析响应 JSON → ChatResponse（text/thinking/tool_use 分离）。 */
    public static ChatResponse parseResponse(String json) {
        Map<String, Object> root = JsonParser.parseObject(json).toMap();
        StringBuilder text = new StringBuilder();
        StringBuilder thinking = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();
        for (Object o : JsonHelper.asList(root.get("content"))) {
            Map<String, Object> block = JsonHelper.asMap(o);
            String type = JsonHelper.str(block.get("type"));
            switch (type) {
                case "text" -> text.append(JsonHelper.str(block.get("text")));
                case "thinking" -> thinking.append(JsonHelper.str(block.get("thinking")));
                case "tool_use" -> toolCalls.add(new ToolCall(
                        JsonHelper.str(block.get("id")),
                        JsonHelper.str(block.get("name")),
                        JsonHelper.asMap(block.get("input"))));
            }
        }
        String stopReason = JsonHelper.str(root.get("stop_reason"));
        TokenUsage usage = parseUsage(JsonHelper.asMap(root.get("usage")));
        return new ChatResponse(text.toString(), thinking.toString(), toolCalls, usage, stopReason, root);
    }

    private static TokenUsage parseUsage(Map<String, Object> usage) {
        if (usage.isEmpty()) {
            return TokenUsage.ZERO;
        }
        return new TokenUsage(JsonHelper.intOf(usage.get("input_tokens")),
                JsonHelper.intOf(usage.get("output_tokens")),
                JsonHelper.intOf(usage.get("cache_read_input_tokens")),
                JsonHelper.intOf(usage.get("cache_creation_input_tokens")));
    }

    /** 流式增量结果（文本/思考）。 */
    public record Delta(String text, boolean thinking) {
    }

    /** 从 SSE data 提取流式文本/thinking 增量（content_block_delta 事件）；工具入参分片由 client 处理。 */
    public static Delta extractStreamDelta(String data) {
        Map<String, Object> event = JsonParser.parseObject(data).toMap();
        String type = JsonHelper.str(event.get("type"));
        if (!"content_block_delta".equals(type)) {
            return null;
        }
        Map<String, Object> delta = JsonHelper.asMap(event.get("delta"));
        String deltaType = JsonHelper.str(delta.get("type"));
        if ("text_delta".equals(deltaType)) {
            return new Delta(JsonHelper.str(delta.get("text")), false);
        }
        if ("thinking_delta".equals(deltaType)) {
            return new Delta(JsonHelper.str(delta.get("thinking")), true);
        }
        return null;
    }
}
