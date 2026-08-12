package com.flora.root.ai.api.provider.protocol;

import com.flora.root.ai.api.ChatRequest;
import com.flora.root.ai.api.ChatResponse;
import com.flora.root.ai.api.ContentBlock;
import com.flora.root.ai.api.Message;
import com.flora.root.ai.api.InferenceConfig;
import com.flora.root.ai.api.TokenUsage;
import com.flora.root.ai.api.ToolCall;
import com.flora.root.ai.api.ToolSpec;
import com.flora.root.ai.api.Thinking;
import com.flora.root.ai.api.impl.JsonHelper;
import com.flora.root.codec.json.JsonBuilder;
import com.flora.root.codec.json.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Google Gemini generateContent API 协议翻译。
 * <p>差异点：{@code contents}/{@code parts} 结构、role {@code model}（映射 assistant）、
 * 顶层 {@code systemInstruction}、Thinking 用 {@code thinkingConfig}。
 * 工具调用用 {@code functionDeclarations}/{@code functionCall}（part 级）；JSON 模式用
 * {@code generationConfig.responseMimeType}。</p>
 */
public final class GeminiProtocol {

    private GeminiProtocol() {
    }

    /** 构建请求体 JSON。 */
    public static String buildRequest(ChatRequest req) {
        return JsonBuilder.toJsonString(buildRequestMap(req, null));
    }

    /** 构建请求体 JSON（可附加 responseFormat 以开启 JSON 模式）。 */
    public static String buildRequest(ChatRequest req, Map<String, Object> responseFormat) {
        return JsonBuilder.toJsonString(buildRequestMap(req, responseFormat));
    }

    public static Map<String, Object> buildRequestMap(ChatRequest req, Map<String, Object> responseFormat) {
        Map<String, Object> body = new LinkedHashMap<>();

        // contents：user → "user"、assistant → "model"；system 走顶层 systemInstruction
        List<Object> contents = new ArrayList<>();
        for (Message m : req.messages()) {
            if (m.role() == Message.Role.SYSTEM) {
                throw new IllegalArgumentException(
                        "Gemini 不支持 SYSTEM 角色消息，请改用 ChatRequest.system() 顶层系统提示");
            }
            Map<String, Object> content = new LinkedHashMap<>();
            String role = m.role() == Message.Role.ASSISTANT ? "model" : "user";
            // 工具结果回执：role=user + functionResponse part
            if (m.role() == Message.Role.TOOL) {
                content.put("role", "user");
                content.put("parts", List.of(functionResponsePart(m)));
                contents.add(content);
                continue;
            }
            // assistant 带工具调用：functionCall part
            if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
                content.put("role", role);
                List<Object> parts = new ArrayList<>();
                for (ToolCall tc : m.toolCalls()) {
                    Map<String, Object> fc = new LinkedHashMap<>();
                    fc.put("name", tc.name());
                    fc.put("args", tc.arguments() == null ? Map.of() : tc.arguments());
                    Map<String, Object> part = new LinkedHashMap<>();
                    part.put("functionCall", fc);
                    parts.add(part);
                }
                content.put("parts", parts);
                contents.add(content);
                continue;
            }
            content.put("role", role);
            content.put("parts", buildParts(m.content()));
            contents.add(content);
        }
        body.put("contents", contents);
        // 顶层 systemInstruction（来自 ChatRequest.system）
        if (req.system() != null && !req.system().isBlank()) {
            Map<String, Object> part = new LinkedHashMap<>();
            part.put("text", req.system());
            Map<String, Object> si = new LinkedHashMap<>();
            si.put("parts", List.of(part));
            body.put("systemInstruction", si);
        }

        // generationConfig
        Map<String, Object> genConfig = new LinkedHashMap<>();
        InferenceConfig c = req.config();
        if (c != null && c.maxTokens() != null) {
            genConfig.put("maxOutputTokens", c.maxTokens());
        }
        // 思考：按强度映射预算
        if (c != null && c.thinking() != null && c.thinking() != Thinking.OFF) {
            Map<String, Object> tc = new LinkedHashMap<>();
            switch (c.thinking()) {
                case Thinking.LOW -> tc.put("thinkingBudget", 1024);
                case Thinking.MEDIUM -> tc.put("thinkingBudget", 4096);
                case Thinking.HIGH, Thinking.MAX ->
                        tc.put("thinkingBudget", 8192);
                case Thinking.AUTO -> tc.put("thinkingBudget", 2048);
            }
            genConfig.put("thinkingConfig", tc);
        }
        // JSON 模式
        if (responseFormat != null && !responseFormat.isEmpty()) {
            genConfig.put("responseMimeType", "application/json");
        }
        if (!genConfig.isEmpty()) {
            body.put("generationConfig", genConfig);
        }

        // 工具声明
        if (req.tools() != null && !req.tools().isEmpty()) {
            List<Object> fns = new ArrayList<>();
            for (ToolSpec t : req.tools()) {
                Map<String, Object> fn = new LinkedHashMap<>();
                fn.put("name", t.name());
                fn.put("description", t.description());
                fn.put("parameters", t.parameters() == null ? Map.of() : t.parameters());
                fns.add(fn);
            }
            body.put("tools", List.of(Map.of("functionDeclarations", fns)));
        }
        return body;
    }

    private static Map<String, Object> functionResponsePart(Message m) {
        Map<String, Object> fr = new LinkedHashMap<>();
        fr.put("name", m.name() == null ? "" : m.name());
        // 执行失败：response 传 {"error": ...}，让模型感知工具异常
        fr.put("response", m.error()
                ? Map.of("error", textOf(m))
                : Map.of("result", textOf(m)));
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("functionResponse", fr);
        return part;
    }

    /** 构建 parts 数组（text/inline_data）。 */
    private static List<Object> buildParts(List<ContentBlock> blocks) {
        List<Object> parts = new ArrayList<>();
        for (ContentBlock b : blocks) {
            switch (b) {
                case ContentBlock.Text text -> {
                    Map<String, Object> part = new LinkedHashMap<>();
                    part.put("text", text.text());
                    parts.add(part);
                }
                case ContentBlock.Image img -> {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("mime_type", img.mediaType());
                    data.put("data", stripDataUrlPrefix(img.dataUrl()));
                    Map<String, Object> part = new LinkedHashMap<>();
                    part.put("inline_data", data);
                    parts.add(part);
                }
                case ContentBlock.Audio audio -> {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("mime_type", audio.mediaType());
                    data.put("data", stripDataUrlPrefix(audio.dataUrl()));
                    Map<String, Object> part = new LinkedHashMap<>();
                    part.put("inline_data", data);
                    parts.add(part);
                }
                default -> throw new IllegalArgumentException(
                        "Gemini 不支持的内容块: " + b.getClass().getSimpleName());
            }
        }
        return parts;
    }

    private static String textOf(Message m) {
        StringBuilder sb = new StringBuilder();
        for (ContentBlock b : m.content()) {
            if (b instanceof ContentBlock.Text text) {
                sb.append(text.text());
            }
        }
        return sb.toString();
    }

    private static String stripDataUrlPrefix(String dataUrl) {
        int comma = dataUrl.indexOf(',');
        return comma >= 0 ? dataUrl.substring(comma + 1) : dataUrl;
    }

    /** 解析响应 JSON → ChatResponse（text/thought part 与 functionCall 分离）。 */
    public static ChatResponse parseResponse(String json) {
        Map<String, Object> root = JsonParser.parseObject(json).toMap();
        StringBuilder text = new StringBuilder();
        StringBuilder thinking = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();
        List<?> candidates = JsonHelper.asList(root.get("candidates"));
        if (!candidates.isEmpty()) {
            Map<String, Object> candidate = JsonHelper.asMap(candidates.get(0));
            Map<String, Object> content = JsonHelper.asMap(candidate.get("content"));
            for (Object o : JsonHelper.asList(content.get("parts"))) {
                Map<String, Object> part = JsonHelper.asMap(o);
                if (part.containsKey("functionCall")) {
                    Map<String, Object> fc = JsonHelper.asMap(part.get("functionCall"));
                    toolCalls.add(new ToolCall(null, JsonHelper.str(fc.get("name")),
                            JsonHelper.asMap(fc.get("args"))));
                    continue;
                }
                String partText = JsonHelper.str(part.get("text"));
                if (partText == null) {
                    continue;
                }
                if (Boolean.TRUE.equals(part.get("thought"))) {
                    thinking.append(partText);
                } else {
                    text.append(partText);
                }
            }
        }
        String finishReason = candidates.isEmpty()
                ? null : JsonHelper.str(JsonHelper.asMap(candidates.get(0)).get("finishReason"));
        TokenUsage usage = parseUsage(JsonHelper.asMap(root.get("usageMetadata")));
        return new ChatResponse(text.toString(), thinking.toString(), toolCalls, usage, finishReason, root);
    }

    private static TokenUsage parseUsage(Map<String, Object> usage) {
        if (usage.isEmpty()) {
            return TokenUsage.ZERO;
        }
        return new TokenUsage(JsonHelper.intOf(usage.get("promptTokenCount")),
                JsonHelper.intOf(usage.get("candidatesTokenCount")), 0, 0);
    }

    /** 从 SSE data 提取流式增量文本（candidates[0].content.parts[0].text）。 */
    public static String extractStreamDelta(String data) {
        Map<String, Object> root = JsonParser.parseObject(data).toMap();
        List<?> candidates = JsonHelper.asList(root.get("candidates"));
        if (candidates.isEmpty()) {
            return null;
        }
        Map<String, Object> content = JsonHelper.asMap(JsonHelper.asMap(candidates.get(0)).get("content"));
        List<?> parts = JsonHelper.asList(content.get("parts"));
        if (parts.isEmpty()) {
            return null;
        }
        return JsonHelper.str(JsonHelper.asMap(parts.get(0)).get("text"));
    }
}
