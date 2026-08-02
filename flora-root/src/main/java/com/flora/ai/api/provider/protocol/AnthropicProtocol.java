package com.flora.ai.api.provider.protocol;

import com.flora.ai.api.ChatRequest;
import com.flora.ai.api.ChatResponse;
import com.flora.ai.api.ContentBlock;
import com.flora.ai.api.Message;
import com.flora.ai.api.SamplingConfig;
import com.flora.ai.api.ThinkingConfig;
import com.flora.ai.api.TokenUsage;
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
 * Extended Thinking 用 {@code thinking} 块、SSE 事件类型为 {@code content_block_delta}。</p>
 */
public final class AnthropicProtocol {

    /** Anthropic API 版本头。 */
    public static final String API_VERSION = "2023-06-01";

    /** max_tokens 默认值（Anthropic 必填）。 */
    public static final int DEFAULT_MAX_TOKENS = 1024;

    private AnthropicProtocol() {
    }

    /** 构建请求体 JSON。 */
    public static String buildRequest(ChatRequest req, String modelId, boolean stream) {
        return JsonBuilder.toJsonString(buildRequestMap(req, modelId, stream));
    }

    public static Map<String, Object> buildRequestMap(ChatRequest req, String modelId, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelId);

        // 顶层 system（SYSTEM 角色消息拼接）
        StringBuilder system = new StringBuilder();
        List<Message> nonSystem = new ArrayList<>();
        for (Message m : req.messages()) {
            if (m.role() == Message.Role.SYSTEM) {
                appendText(system, m);
            } else {
                nonSystem.add(m);
            }
        }
        if (system.length() > 0) {
            body.put("system", system.toString());
        }

        body.put("messages", buildMessages(nonSystem));

        // max_tokens 必填
        int maxTokens = DEFAULT_MAX_TOKENS;
        SamplingConfig s = req.sampling();
        if (s != null && s.maxTokens() != null) {
            maxTokens = s.maxTokens();
        }
        body.put("max_tokens", maxTokens);

        if (s != null) {
            if (s.temperature() != null) {
                body.put("temperature", s.temperature());
            }
            if (s.topP() != null) {
                body.put("top_p", s.topP());
            }
        }

        ThinkingConfig t = req.thinking();
        if (t != null && t.enabled()) {
            Map<String, Object> thinking = new LinkedHashMap<>();
            thinking.put("type", "enabled");
            if (t.budgetTokens() != null) {
                thinking.put("budget_tokens", t.budgetTokens());
            } else {
                thinking.put("budget_tokens", Math.max(1024, maxTokens));
            }
            body.put("thinking", thinking);
        }

        if (stream) {
            body.put("stream", true);
        }
        return body;
    }

    private static void appendText(StringBuilder sb, Message m) {
        for (ContentBlock b : m.content()) {
            if (b instanceof ContentBlock.Text text) {
                if (sb.length() > 0) {
                    sb.append("\n\n");
                }
                sb.append(text.text());
            }
        }
    }

    /** 构建 messages 数组（content 为块数组）。 */
    private static List<Object> buildMessages(List<Message> messages) {
        List<Object> list = new ArrayList<>();
        for (Message m : messages) {
            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("role", m.role().name().toLowerCase());
            msg.put("content", buildContentBlocks(m.content()));
            list.add(msg);
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

    /** 解析响应 JSON → ChatResponse（text/thinking 块分离）。 */
    public static ChatResponse parseResponse(String json) {
        Map<String, Object> root = JsonParser.parseObject(json);
        StringBuilder text = new StringBuilder();
        StringBuilder thinking = new StringBuilder();
        for (Object o : JsonHelper.asList(root.get("content"))) {
            Map<String, Object> block = JsonHelper.asMap(o);
            String type = JsonHelper.str(block.get("type"));
            if ("text".equals(type)) {
                text.append(JsonHelper.str(block.get("text")));
            } else if ("thinking".equals(type)) {
                thinking.append(JsonHelper.str(block.get("thinking")));
            }
        }
        String stopReason = JsonHelper.str(root.get("stop_reason"));
        TokenUsage usage = parseUsage(JsonHelper.asMap(root.get("usage")));
        return new ChatResponse(text.toString(), thinking.toString(), List.of(), usage, stopReason, root);
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

    /** 流式增量结果。 */
    public record Delta(String text, boolean thinking) {
    }

    /** 从 SSE data 提取流式增量（content_block_delta 事件）；非增量事件返回 null。 */
    public static Delta extractStreamDelta(String data) {
        Map<String, Object> event = JsonParser.parseObject(data);
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
