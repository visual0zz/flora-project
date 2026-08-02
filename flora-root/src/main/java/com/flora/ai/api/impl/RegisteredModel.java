package com.flora.ai.api.impl;

import com.flora.codec.json.JsonParser;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 注册的模型：唯一的模型描述类型。
 * <p>区分两部分：{@code spec}（技术规格，定制化 Map，不同模型不同 key，如
 * {@code contextWindow}/{@code modalities}）与 {@code tags}（系统统一能力标签）。
 * 另含端点配置（{@code apiKind}/{@code baseUrl}/{@code apiKey}）与
 * {@code isDefault}（是否默认，路由 fallback 目标）及 {@code extra}（用户自定义附加参数）。</p>
 */
public record RegisteredModel(
        String id,
        ApiKind apiKind,
        String modelId,
        String baseUrl,
        String apiKey,
        boolean isDefault,
        Set<Tag> tags,
        Map<String, Object> spec,
        Map<String, Object> extra) {

    /**
     * 便捷构造：无附加参数。
     */
    public static RegisteredModel of(String id, ApiKind apiKind, String modelId,
                                     String baseUrl, String apiKey, boolean isDefault,
                                     Set<Tag> tags, Map<String, Object> spec) {
        return new RegisteredModel(id, apiKind, modelId, baseUrl, apiKey, isDefault,
                Set.copyOf(tags == null ? Set.of() : tags),
                spec == null ? Map.of() : Map.copyOf(spec), Map.of());
    }

    /** 便捷构造：自动生成 id（baseUrl + apiKind 派生）。 */
    public static RegisteredModel of(ApiKind apiKind, String modelId,
                                     String baseUrl, String apiKey) {
        return new RegisteredModel(autoId(apiKind, baseUrl), apiKind, modelId, baseUrl, apiKey,
                false, Set.of(), Map.of(), Map.of());
    }

    /**
     * 解析注册 JSON。
     * <p>核心字段：{@code id}/{@code apiKind}/{@code modelId}/{@code baseUrl}/
     * {@code apiKey}/{@code default}/{@code tags}/{@code spec}。除核心字段外的所有
     * 附加字段保留到 {@code extra}（供 Router 读取）。{@code apiKind} 为 {@link ApiKind}
     * 枚举名。</p>
     */
    public static RegisteredModel fromJson(String json) {
        Map<String, Object> m = JsonParser.parseObject(json);
        ApiKind kind = ApiKind.valueOf(String.valueOf(m.get("apiKind")));
        String modelId = String.valueOf(m.get("modelId"));
        String baseUrl = String.valueOf(m.get("baseUrl"));
        String apiKey = m.get("apiKey") == null ? null : String.valueOf(m.get("apiKey"));

        Object idObj = m.get("id");
        String id = idObj != null ? String.valueOf(idObj) : autoId(kind, baseUrl);
        boolean isDefault = Boolean.TRUE.equals(m.get("default"));

        Set<Tag> tags = new LinkedHashSet<>();
        if (m.get("tags") instanceof Iterable<?> tagList) {
            for (Object t : tagList) {
                tags.add(Tag.valueOf(String.valueOf(t)));
            }
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = m.get("spec") instanceof Map<?, ?> sm
                ? (Map<String, Object>) sm : Map.of();

        Map<String, Object> extra = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> e : m.entrySet()) {
            switch (e.getKey()) {
                case "id", "apiKind", "modelId", "baseUrl", "apiKey", "default", "tags", "spec" -> {
                    // 核心字段，不进入 extra
                }
                default -> extra.put(e.getKey(), e.getValue());
            }
        }
        return new RegisteredModel(id, kind, modelId, baseUrl, apiKey, isDefault,
                Set.copyOf(tags), Map.copyOf(spec), Map.copyOf(extra));
    }

    private static String autoId(ApiKind kind, String baseUrl) {
        return kind.name() + "@" + baseUrl;
    }
}
