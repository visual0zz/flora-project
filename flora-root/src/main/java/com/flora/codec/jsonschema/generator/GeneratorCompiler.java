package com.flora.codec.jsonschema.generator;

import com.flora.codec.jsonschema.SchemaRegistry;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * schema → 生成规则编译器。记忆化缓存节点，处理 {@code $ref}/{@code $defs}，
 * 编译期合并 {@code allOf} 分支约束（取交集）。
 */
final class GeneratorCompiler {

    private final SchemaRegistry registry;
    private final Map<Object, GenerationNode> cache = new IdentityHashMap<>();
    private final Object rootNode;

    private GeneratorCompiler(SchemaRegistry registry, Object rootNode) {
        this.registry = registry;
        this.rootNode = rootNode;
    }

    static GeneratorCompiler of(Object rootNode) {
        return new GeneratorCompiler(SchemaRegistry.of(rootNode), rootNode);
    }

    SchemaRegistry registry() {
        return registry;
    }

    GenerationNode root() {
        return compile(rootNode, "");
    }

    GenerationNode compile(Object node, String baseUri) {
        if (node instanceof Boolean b) {
            return new GenerationNode(b, this);
        }
        if (!(node instanceof Map)) {
            throw new JsonGenerationException("schema 必须是对象或布尔值: " + node);
        }
        GenerationNode cached = cache.get(node);
        if (cached != null) {
            return cached;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> schema = (Map<String, Object>) node;
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
        return merged;
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
