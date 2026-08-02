package com.flora.ai.api;

import com.flora.codec.json.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 端点：一个可注册、可路由的模型接入点，对应一种单能力 client。
 * <p>与 client 一一对齐：一个 Endpoint 承载一个端点的一种能力（{@code capability}）。
 * 注册 JSON 中的 {@code capabilities} 数组会被展开为多个 Endpoint（id = {@code 原id:capability}）。
 * 其余字段：端点配置（{@code apiKind}/{@code baseUrl}/{@code apiKey}）、协议模型标识
 * （{@code modelId}）、是否默认（{@code isDefault}）、定制化技术规格（{@code spec}）
 * 及附加参数（{@code extra}）。client 的能力集合（思考/JSON/多模态/工具）由 client
 * 自身声明（{@link ApiClient#capabilities()}），不在端点配置。</p>
 */
public record Endpoint(
        String id,
        ApiSchema apiKind,
        String modelId,
        String baseUrl,
        String apiKey,
        boolean isDefault,
        IOMode capability,
        Map<String, Object> spec,
        Map<String, Object> extra) {

    /** 便捷构造。 */
    public static Endpoint of(String id, ApiSchema apiKind, String modelId,
                              String baseUrl, String apiKey, boolean isDefault,
                              IOMode capability, Map<String, Object> spec) {
        return new Endpoint(id, apiKind, modelId, baseUrl, apiKey, isDefault, capability,
                spec == null ? Map.of() : Map.copyOf(spec), Map.of());
    }

    /** 便捷构造：自动生成 id（baseUrl + apiKind 派生），默认 CHAT 能力。 */
    public static Endpoint of(ApiSchema apiKind, String modelId,
                              String baseUrl, String apiKey) {
        return new Endpoint(autoId(apiKind, baseUrl), apiKind, modelId, baseUrl, apiKey,
                false, IOMode.CHAT, Map.of(), Map.of());
    }

    /**
     * 解析注册 JSON 并展开为多个 Endpoint（每能力一个，id = {@code 原id:capability}）。
     * <p>核心字段：{@code id}/{@code apiKind}/{@code modelId}/{@code baseUrl}/
     * {@code apiKey}/{@code default}/{@code capabilities}/{@code spec}。
     * {@code capabilities} 未声明时默认 {@code ["CHAT"]}。除核心字段外的附加字段
     * 保留到 {@code extra}（供 Router 读取）。</p>
     */
    public static List<Endpoint> fromJsonAll(String json) {
        Map<String, Object> m = JsonParser.parseObject(json);
        ApiSchema kind = ApiSchema.valueOf(String.valueOf(m.get("apiKind")));
        Object modelObj = m.get("modelId");
        String modelId = modelObj == null ? null : String.valueOf(modelObj);
        String baseUrl = m.get("baseUrl") == null ? null : String.valueOf(m.get("baseUrl"));
        String apiKey = m.get("apiKey") == null ? null : String.valueOf(m.get("apiKey"));

        Object idObj = m.get("id");
        String baseId = idObj != null ? String.valueOf(idObj) : autoId(kind, baseUrl);
        boolean isDefault = Boolean.TRUE.equals(m.get("default"));

        // capabilities：未声明默认 CHAT
        List<IOMode> capabilities = new ArrayList<>();
        if (m.get("capabilities") instanceof Iterable<?> capList) {
            for (Object c : capList) {
                capabilities.add(IOMode.valueOf(String.valueOf(c)));
            }
        }
        if (capabilities.isEmpty()) {
            capabilities.add(IOMode.CHAT);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> spec = m.get("spec") instanceof Map<?, ?> sm
                ? (Map<String, Object>) sm : Map.of();

        Map<String, Object> extra = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> e : m.entrySet()) {
            switch (e.getKey()) {
                case "id", "apiKind", "modelId", "baseUrl", "apiKey", "default",
                        "capabilities", "spec" -> {
                    // 核心字段，不进入 extra
                }
                default -> extra.put(e.getKey(), e.getValue());
            }
        }

        List<Endpoint> endpoints = new ArrayList<>();
        for (IOMode c : capabilities) {
            endpoints.add(new Endpoint(baseId + ":" + c.name(), kind, modelId, baseUrl, apiKey,
                    isDefault, c, Map.copyOf(spec), Map.copyOf(extra)));
        }
        return endpoints;
    }

    private static String autoId(ApiSchema kind, String baseUrl) {
        return kind.name() + "@" + baseUrl;
    }
}
