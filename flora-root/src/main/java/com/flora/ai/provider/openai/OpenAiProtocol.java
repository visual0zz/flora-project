package com.flora.ai.provider.openai;

import com.flora.ai.api.ChatRequest;
import com.flora.ai.api.ChatResponse;
import com.flora.ai.api.ContentBlock;
import com.flora.ai.api.Message;
import com.flora.ai.api.SamplingConfig;
import com.flora.ai.api.ThinkingConfig;
import com.flora.ai.api.TokenUsage;
import com.flora.ai.provider.JsonHelper;
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
final class OpenAiProtocol {

    private OpenAiProtocol() {
    }

    /** 构建请求体 JSON（{@code stream=false}）。 */
    static String buildRequest(ChatRequest req) {
        return JsonBuilder.toJsonString(buildRequestMap(req, false));
    }

    static Map<String, Object> buildRequestMap(ChatRequest req, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", req.model().id());
        body.put("messages", buildMessages(req.messages()));

        SamplingConfig s = req.sampling();
        if (s != null) {
            if (s.temperature() != null) {
                body.put("temperature", s.temperature());
            }
            if (s.topP() != null) {
                body.put("top_p", s.topP());
            }
            if (s.maxTokens() != null) {
                body.put("max_tokens", s.maxTokens());
            }
            if (s.seed() != null) {
                body.put("seed", s.seed());
            }
        }

        ThinkingConfig t = req.thinking();
        if (t != null && t.enabled() && t.effort() != null) {
            body.put("reasoning_effort", switch (t.effort()) {
                case LOW -> "low";
                case MEDIUM -> "medium";
                case HIGH -> "high";
                case MAX -> "high"; // OpenAI 无 max，映射到 high
            });
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
            // 纯文本单块 → String；否则内容块数组
            if (m.content().size() == 1 && m.content().get(0) instanceof ContentBlock.Text text) {
                msg.put("content", text.text());
            } else {
                msg.put("content", buildContentBlocks(m.content()));
            }
            list.add(msg);
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
    static ChatResponse parseResponse(String json) {
        Map<String, Object> root = JsonParser.parseObject(json);
        List<?> choices = JsonHelper.asList(root.get("choices"));
        Map<?, ?> choice = choices.isEmpty() ? null : JsonHelper.asMap(choices.get(0));
        Map<?, ?> message = choice == null ? null : JsonHelper.asMap(choice.get("message"));

        String text = message == null ? null : JsonHelper.str(message.get("content"));
        String thinking = message == null ? null : JsonHelper.str(message.get("reasoning_content"));
        String stopReason = choice == null ? null : JsonHelper.str(choice.get("finish_reason"));
        TokenUsage usage = parseUsage(JsonHelper.asMap(root.get("usage")));
        return new ChatResponse(text, thinking, usage, stopReason, root);
    }

    private static TokenUsage parseUsage(Map<?, ?> usage) {
        if (usage == null) {
            return TokenUsage.ZERO;
        }
        return new TokenUsage(JsonHelper.intOf(usage.get("prompt_tokens")), JsonHelper.intOf(usage.get("completion_tokens")),
                JsonHelper.intOf(usage.get("prompt_tokens_details")), 0);
    }
}
