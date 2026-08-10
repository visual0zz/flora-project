package com.flora.mock.jsonschema.impl;

import com.flora.codec.json.model.JsonBool;
import com.flora.codec.json.model.JsonObject;
import com.flora.codec.json.model.JsonValue;
import com.flora.codec.jsonschema.impl.SchemaRegistry;
import com.flora.mock.jsonschema.JsonGenerationException;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * schema → 生成规则编译器。记忆化缓存节点，处理 {@code $ref}/{@code $defs}，
 * 编译期合并 {@code allOf} 分支约束（取交集）。
 * <p>对外入口（{@link #of}）接受 {@link JsonValue} 模型（{@link JsonObject}/{@link JsonBool}）；
 * 内部以 {@code Map<String,Object>} 工作副本承载合并后的 schema（实现细节，非对外 schema 输入），
 * 故 {@link #compile(Object, String)} 同时接受 {@link JsonObject} 与裸 {@code Map} 工作副本。</p>
 */
public final class GeneratorCompiler {

    private final SchemaRegistry registry;
    private final Map<Object, GenerationNode> cache = new IdentityHashMap<>();
    private final JsonValue rootNode;

    private GeneratorCompiler(SchemaRegistry registry, JsonValue rootNode) {
        this.registry = registry;
        this.rootNode = rootNode;
    }

    public static GeneratorCompiler of(JsonValue rootNode) {
        return new GeneratorCompiler(SchemaRegistry.of(rootNode), rootNode);
    }

    public GenerationNode root() {
        return compile(rootNode, "");
    }

    GenerationNode compile(Object node, String baseUri) {
        if (node instanceof JsonBool b) {
            return new GenerationNode(b.value(), this);
        }
        if (!(node instanceof JsonObject) && !(node instanceof Map<?, ?>)) {
            throw new JsonGenerationException("schema 必须是对象或布尔值: " + node);
        }
        GenerationNode cached = cache.get(node);
        if (cached != null) {
            return cached;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> schema = node instanceof JsonObject jo ? jo.toMap()
                : (Map<String, Object>) node;
        Map<String, Object> effective = mergeAllOf(schema, baseUri);
        GenerationNode gn = new GenerationNode(effective, baseUri, this);
        cache.put(node, gn);
        return gn;
    }

    GenerationNode resolveRef(String ref, String baseUri) {
        Object target = registry.resolveNode(ref, baseUri);
        return compile(target, baseUri);
    }

    // ── allOf 合并：常用约束取交集 ──

    /** 内部标记 key：allOf 合并后待满足的全部 pattern（List<String>）。 */
    private static final String PATTERNS_KEY = "_patterns";

    private Map<String, Object> mergeAllOf(Map<String, Object> schema, String baseUri) {
        if (!(schema.get("allOf") instanceof List<?> allOf) || allOf.isEmpty()) {
            return schema;
        }
        Map<String, Object> merged = new LinkedHashMap<>(schema);
        merged.remove("allOf");
        for (Object branch : allOf) {
            if (branch instanceof Map<?, ?> bm) {
                mergeInto(merged, bm);
            }
        }
        // 主 schema 自身的 pattern 也纳入交集
        if (merged.get("pattern") instanceof String selfPattern) {
            addPattern(merged, selfPattern);
        }
        return merged;
    }

    private static void addPattern(Map<String, Object> merged, String pattern) {
        @SuppressWarnings("unchecked")
        List<String> patterns = (List<String>) merged.computeIfAbsent(PATTERNS_KEY,
                k -> new java.util.ArrayList<String>());
        if (!patterns.contains(pattern)) {
            patterns.add(pattern);
        }
    }

    @SuppressWarnings("unchecked")
    private void mergeInto(Map<String, Object> merged, Map<?, ?> branch) {
        if (!merged.containsKey("type") && branch.containsKey("type")) {
            merged.put("type", branch.get("type"));
        }
        if (!merged.containsKey("enum") && branch.containsKey("enum")) {
            merged.put("enum", branch.get("enum"));
        }
        if (!merged.containsKey("const") && branch.containsKey("const")) {
            merged.put("const", branch.get("const"));
        }
        mergeNumericMax(merged, branch, "minimum");
        mergeNumericMax(merged, branch, "exclusiveMinimum");
        mergeNumericMin(merged, branch, "maximum");
        mergeNumericMin(merged, branch, "exclusiveMaximum");
        if (!merged.containsKey("multipleOf") && branch.containsKey("multipleOf")) {
            merged.put("multipleOf", branch.get("multipleOf"));
        }
        mergeNumericMax(merged, branch, "minLength");
        mergeNumericMin(merged, branch, "maxLength");
        if (!merged.containsKey("format") && branch.containsKey("format")) {
            merged.put("format", branch.get("format"));
        }
        mergeNumericMax(merged, branch, "minItems");
        mergeNumericMin(merged, branch, "maxItems");
        if (!merged.containsKey("required") && branch.containsKey("required")) {
            merged.put("required", branch.get("required"));
        }
        if (branch.get("pattern") instanceof String bp) {
            addPattern(merged, bp);
        }
        if (branch.get("properties") instanceof Map<?, ?> bp) {
            Map<String, Object> props = (Map<String, Object>) merged.computeIfAbsent("properties",
                    k -> new LinkedHashMap<String, Object>());
            for (Map.Entry<?, ?> e : bp.entrySet()) {
                props.putIfAbsent(String.valueOf(e.getKey()), e.getValue());
            }
        }
    }

    private static void mergeNumericMax(Map<String, Object> merged, Map<?, ?> branch, String key) {
        if (!merged.containsKey(key) && branch.get(key) instanceof Number n) {
            merged.put(key, n);
        } else if (branch.get(key) instanceof Number b && merged.get(key) instanceof Number m
                && m.doubleValue() < b.doubleValue()) {
            merged.put(key, b);
        }
    }

    private static void mergeNumericMin(Map<String, Object> merged, Map<?, ?> branch, String key) {
        if (!merged.containsKey(key) && branch.get(key) instanceof Number n) {
            merged.put(key, n);
        } else if (branch.get(key) instanceof Number b && merged.get(key) instanceof Number m
                && m.doubleValue() > b.doubleValue()) {
            merged.put(key, b);
        }
    }
}
