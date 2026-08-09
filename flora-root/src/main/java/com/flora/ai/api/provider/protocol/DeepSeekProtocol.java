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
 * DeepSeek 官方协议翻译（独立于 OpenAI 协议）。
 * <p>DeepSeek API 为 OpenAI Chat Completions 兼容格式，差异点：
 * JSON 模式仅支持 {@code json_object}（不支持 {@code json_schema}）；
 * {@code deepseek-reasoner} 模型不支持工具调用（请求带 tools 抛异常）。</p>
 */
public final class DeepSeekProtocol {

    /** reasoner 模型标识子串。 */
    public static final String REASONER = "reasoner";

    private DeepSeekProtocol() {
    }

    /** 构建请求体 JSON。 */
    public static String buildRequest(ChatRequest req, String modelId, boolean stream) {
        return JsonBuilder.toJsonString(buildRequestMap(req, modelId, stream, null));
    }

    /** 构建请求体 Map；responseFormat 仅接受 json_object。 */
    public static Map<String, Object> buildRequestMap(ChatRequest req, String modelId, boolean stream,
                                                      Map<String, Object> responseFormat) {
        // reasoner 模型不支持工具调用
        if (modelId != null && modelId.contains(REASONER)
                && req.tools() != null && !req.tools().isEmpty()) {
            throw new IllegalArgumentException("deepseek-reasoner 不支持工具调用");
        }
        // JSON 模式仅 json_object
        if (responseFormat != null && !responseFormat.isEmpty()
                && !"json_object".equals(responseFormat.get("type"))) {
            throw new IllegalArgumentException("DeepSeek 仅支持 json_object，不支持: " + responseFormat.get("type"));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelId);
        List<Object> messages = buildMessages(req.messages());
        // 顶层 system 字段 → 合成 system 消息（DeepSeek 兼容 OpenAI 格式）
        if (req.system() != null && !req.system().isBlank()) {
            Map<String, Object> sys = new LinkedHashMap<>();
            sys.put("role", "system");
            sys.put("content", req.system());
            messages.add(0, sys);
        }
        body.put("messages", messages);
        if (req.tools() != null && !req.tools().isEmpty()) {
            body.put("tools", buildTools(req.tools()));
        }
        if (responseFormat != null && !responseFormat.isEmpty()) {
            body.put("response_format", responseFormat);
        }

        InferenceConfig c = req.config();
        if (c != null) {
            if (c.maxTokens() != null) {
                body.put("max_tokens", c.maxTokens());
            }
            if (c.thinking() != null) {
                switch (c.thinking()) {
                    case LOW -> body.put("reasoning_effort", "low");
                    case MEDIUM -> body.put("reasoning_effort", "medium");
                    case HIGH, MAX -> body.put("reasoning_effort", "high");
                    case OFF, AUTO -> { /* 不传 reasoning_effort */ }
                }
            }
        }
        if (stream) {
            body.put("stream", true);
        }
        return body;
    }

    private static List<Object> buildMessages(List<Message> messages) {
        List<Object> list = new ArrayList<>();
        for (Message m : messages) {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("role", m.role().name().toLowerCase());
            if (m.role() == Message.Role.TOOL) {
                msg.put("tool_call_id", m.toolCallId() == null ? "" : m.toolCallId());
                msg.put("content", textOf(m));
                list.add(msg);
                continue;
            }
            if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
                msg.put("content", textOf(m));
                msg.put("tool_calls", buildToolCalls(m.toolCalls()));
                list.add(msg);
                continue;
            }
            if (m.content().size() == 1 && m.content().get(0) instanceof ContentBlock.Text text) {
                msg.put("content", text.text());
            } else {
                msg.put("content", buildContentBlocks(m.content()));
            }
            list.add(msg);
        }
        return list;
    }

    private static String textOf(Message m) {
        StringBuilder sb = new StringBuilder();
        for (ContentBlock b : m.content()) {
            if (b instanceof ContentBlock.Text(String text)) {
                sb.append(text);
            }
        }
        return sb.toString();
    }

    private static List<Object> buildToolCalls(List<ToolCall> calls) {
        List<Object> list = new ArrayList<>();
        for (ToolCall c : calls) {
            Map<String, Object> call = new LinkedHashMap<>();
            call.put("id", c.id());
            Map<String, Object> fn = new LinkedHashMap<>();
            fn.put("name", c.name());
            fn.put("arguments", JsonBuilder.toJsonString(c.arguments()));
            call.put("function", fn);
            list.add(call);
        }
        return list;
    }

    private static List<Object> buildTools(List<ToolSpec> tools) {
        List<Object> list = new ArrayList<>();
        for (ToolSpec t : tools) {
            Map<String, Object> fn = new LinkedHashMap<>();
            fn.put("name", t.name());
            fn.put("description", t.description());
            fn.put("parameters", t.parameters());
            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("type", "function");
            tool.put("function", fn);
            list.add(tool);
        }
        return list;
    }

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
                default -> throw new IllegalArgumentException(
                        "DeepSeek 不支持的内容块: " + b.getClass().getSimpleName());
            }
        }
        return list;
    }

    /** 解析响应 JSON → ChatResponse（含 tool_calls）。 */
    public static ChatResponse parseResponse(String json) {
        Map<String, Object> root = JsonParser.parseObject(json).toMap();
        List<?> choices = JsonHelper.asList(root.get("choices"));
        Map<?, ?> choice = choices.isEmpty() ? null : JsonHelper.asMap(choices.getFirst());
        Map<?, ?> message = choice == null ? null : JsonHelper.asMap(choice.get("message"));
        String text = message == null ? null : JsonHelper.str(message.get("content"));
        String thinking = message == null ? null : JsonHelper.str(message.get("reasoning_content"));
        List<ToolCall> toolCalls = message == null ? List.of() : parseToolCalls(message.get("tool_calls"));
        String stopReason = choice == null ? null : JsonHelper.str(choice.get("finish_reason"));
        TokenUsage usage = parseUsage(JsonHelper.asMap(root.get("usage")));
        return new ChatResponse(text, thinking, toolCalls, usage, stopReason, root);
    }

    @SuppressWarnings("unchecked")
    private static List<ToolCall> parseToolCalls(Object o) {
        List<ToolCall> calls = new ArrayList<>();
        for (Object item : JsonHelper.asList(o)) {
            Map<String, Object> call = JsonHelper.asMap(item);
            Map<String, Object> fn = JsonHelper.asMap(call.get("function"));
            String argsJson = JsonHelper.str(fn.get("arguments"));
            Map<String, Object> args = Map.of();
            if (argsJson != null && !argsJson.isBlank()) {
                Object parsed = JsonParser.parse(argsJson).toMap();
                if (parsed instanceof Map<?, ?> pm) {
                    args = (Map<String, Object>) pm;
                }
            }
            calls.add(new ToolCall(JsonHelper.str(call.get("id")),
                    JsonHelper.str(fn.get("name")), args));
        }
        return calls;
    }

    private static TokenUsage parseUsage(Map<?, ?> usage) {
        if (usage == null) {
            return TokenUsage.ZERO;
        }
        return new TokenUsage(JsonHelper.intOf(usage.get("prompt_tokens")),
                JsonHelper.intOf(usage.get("completion_tokens")), 0, 0);
    }

    /** 流式增量结果。 */
    public record Delta(String text, boolean thinking) {
    }

    /** 从 SSE data 提取流式增量（choices[0].delta）。 */
    public static Delta extractStreamDelta(String data) {
        Map<String, Object> root = JsonParser.parseObject(data).toMap();
        List<?> choices = JsonHelper.asList(root.get("choices"));
        if (choices.isEmpty()) {
            return null;
        }
        Map<String, Object> delta = JsonHelper.asMap(JsonHelper.asMap(choices.get(0)).get("delta"));
        String text = JsonHelper.str(delta.get("content"));
        if (text != null && !text.isEmpty()) {
            return new Delta(text, false);
        }
        String thinking = JsonHelper.str(delta.get("reasoning_content"));
        if (thinking != null && !thinking.isEmpty()) {
            return new Delta(thinking, true);
        }
        return null;
    }
}
