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
 * OpenAI Chat Completions 协议翻译。
 * <p>把统一 {@link ChatRequest} 翻译为 OpenAI 请求 JSON，并解析响应 JSON。
 * 逻辑与网络解耦，便于单测。</p>
 */
public final class OpenAiProtocol {

    private OpenAiProtocol() {
    }

    /** 构建请求体 JSON（{@code stream=false}）。 */
    public static String buildRequest(ChatRequest req, String modelId) {
        return JsonBuilder.toJsonString(buildRequestMap(req, modelId, false));
    }

    public static Map<String, Object> buildRequestMap(ChatRequest req, String modelId, boolean stream) {
        return buildRequestMap(req, modelId, stream, null);
    }

    /** 构建请求体 Map，支持 tools 与 JSON 模式（responseFormat 如 {"type":"json_object"}）。 */
    public static Map<String, Object> buildRequestMap(ChatRequest req, String modelId, boolean stream,
                                                      Map<String, Object> responseFormat) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelId);
        List<Object> messages = buildMessages(req.messages());
        // 顶层 system 字段 → 合成 system 消息（OpenAI 原生无顶层 system）
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
            if (c.seed() != null) {
                body.put("seed", c.seed());
            }
            if (c.maxTokens() != null) {
                body.put("max_tokens", c.maxTokens());
            }
            if (c.seed() != null) {
                body.put("seed", c.seed());
            }
            if (c.thinking() != null) {
                switch (c.thinking()) {
                    case LOW -> body.put("reasoning_effort", "low");
                    case MEDIUM -> body.put("reasoning_effort", "medium");
                    case HIGH, MAX -> body.put("reasoning_effort", "high"); // OpenAI 无 max
                    case OFF, AUTO -> { /* 不传 reasoning_effort */ }
                }
            }
        }

        if (stream) {
            body.put("stream", true);
        }
        return body;
    }

    /** 构建 messages 数组。 */
    private static List<Object> buildMessages(List<Message> messages) {
        List<Object> list = new ArrayList<>();
        for (Message m : messages) {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("role", m.role().name().toLowerCase());
            // TOOL 角色：带 tool_call_id 回执
            if (m.role() == Message.Role.TOOL) {
                msg.put("tool_call_id", m.toolCallId() == null ? "" : m.toolCallId());
                msg.put("content", textOf(m));
                list.add(msg);
                continue;
            }
            // ASSISTANT 带工具调用：content 用 null + tool_calls
            if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
                msg.put("content", textOf(m));
                msg.put("tool_calls", buildToolCalls(m.toolCalls()));
                list.add(msg);
                continue;
            }
            // 纯文本单块 → String；否则内容块数组
            if (m.content().size() == 1 && m.content().getFirst() instanceof ContentBlock.Text(String text1)) {
                msg.put("content", text1);
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
            if (b instanceof ContentBlock.Text(String text1)) {
                sb.append(text1);
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

    /** 构建 tools 数组。 */
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

    /** 构建多模态 content 块数组（text/image_url）。 */
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
                    Map<String, Object> inner = new LinkedHashMap<>();
                    inner.put("url", img.dataUrl());
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("type", "image_url");
                    m.put("image_url", inner);
                    list.add(m);
                }
                default -> throw new IllegalArgumentException("OpenAI 不支持的内容块: " + b.getClass().getSimpleName());
            }
        }
        return list;
    }

    /** 解析响应 JSON → ChatResponse。 */
    public static ChatResponse parseResponse(String json) {
        Map<String, Object> root = JsonParser.parseObject(json);
        List<?> choices = JsonHelper.asList(root.get("choices"));
        Map<?, ?> choice = choices.isEmpty() ? null : JsonHelper.asMap(choices.get(0));
        Map<?, ?> message = choice == null ? null : JsonHelper.asMap(choice.get("message"));

        String text = message == null ? null : JsonHelper.str(message.get("content"));
        String thinking = message == null ? null : JsonHelper.str(message.get("reasoning_content"));
        List<ToolCall> toolCalls = message == null ? List.of() : parseToolCalls(message.get("tool_calls"));
        String stopReason = choice == null ? null : JsonHelper.str(choice.get("finish_reason"));
        TokenUsage usage = parseUsage(JsonHelper.asMap(root.get("usage")));
        return new ChatResponse(text, thinking, toolCalls, usage, stopReason, root);
    }

    /** 解析 tool_calls → List<ToolCall>（arguments 为 JSON 串，解析为 Map）。 */
    @SuppressWarnings("unchecked")
    private static List<ToolCall> parseToolCalls(Object o) {
        List<ToolCall> calls = new ArrayList<>();
        for (Object item : JsonHelper.asList(o)) {
            Map<String, Object> call = JsonHelper.asMap(item);
            Map<String, Object> fn = JsonHelper.asMap(call.get("function"));
            String argsJson = JsonHelper.str(fn.get("arguments"));
            Map<String, Object> args = Map.of();
            if (argsJson != null && !argsJson.isBlank()) {
                Object parsed = JsonParser.parse(argsJson);
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
        return new TokenUsage(JsonHelper.intOf(usage.get("prompt_tokens")), JsonHelper.intOf(usage.get("completion_tokens")),
                JsonHelper.intOf(usage.get("prompt_tokens_details")), 0);
    }
}
