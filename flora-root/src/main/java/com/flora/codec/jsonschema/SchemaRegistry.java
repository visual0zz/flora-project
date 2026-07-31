package com.flora.codec.jsonschema;

import com.flora.codec.jsonschema.validator.ArrayValidator;
import com.flora.codec.jsonschema.validator.CombinatorValidator;
import com.flora.codec.jsonschema.validator.EnumValidator;
import com.flora.codec.jsonschema.validator.FormatValidator;
import com.flora.codec.jsonschema.validator.KeywordValidator;
import com.flora.codec.jsonschema.validator.NumericValidator;
import com.flora.codec.jsonschema.validator.ObjectValidator;
import com.flora.codec.jsonschema.validator.RefValidator;
import com.flora.codec.jsonschema.validator.StringValidator;
import com.flora.codec.jsonschema.validator.TypeValidator;
import com.flora.codec.jsonschema.validator.UnevaluatedValidator;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * schema 编译与引用解析注册表。
 * <p>编译期：记忆化编译每个 schema 节点，处理 {@code $id}（注册）、
 * {@code $anchor}/{@code $dynamicAnchor}（注册）、递归编译子 schema 与 {@code $defs}。
 * 解析期：{@code $ref}/{@code $dynamicRef} 经 JSON Pointer / anchor / {@code $id} 定位并编译目标。
 * 节点先入缓存，支持递归引用的循环防护。</p>
 */
public final class SchemaRegistry {

    private final Map<Object, CompiledSchema> cache = new IdentityHashMap<>();
    private final Map<String, Object> idToNode = new java.util.HashMap<>();
    private final Map<String, Object> anchorToNode = new java.util.HashMap<>();
    private final Object rootNode;
    private CompiledSchema rootSchema;

    private SchemaRegistry(Object rootNode) {
        this.rootNode = rootNode;
    }

    /** 编译根 schema 并创建注册表。 */
    public static SchemaRegistry of(Object rootNode) {
        SchemaRegistry reg = new SchemaRegistry(rootNode);
        reg.rootSchema = reg.compileNode(rootNode, "");
        return reg;
    }

    public CompiledSchema root() {
        return rootSchema;
    }

    /** 记忆化编译节点。布尔 schema 编译为恒真/恒假。 */
    public CompiledSchema compileNode(Object node, String baseUri) {
        if (node instanceof Boolean b) {
            CompiledSchema result = b ? CompiledSchema.always() : CompiledSchema.never();
            cache.put(node, result);
            return result;
        }
        if (!(node instanceof Map)) {
            throw new IllegalArgumentException("schema 必须是对象或布尔值: " + node);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) node;
        CompiledSchema cached = cache.get(node);
        if (cached != null) {
            return cached;
        }
        String id = str(map.get("$id"));
        String resolvedBase = id != null ? resolveUri(baseUri, id) : baseUri;
        CompiledSchema schema = CompiledSchema.newSchema(resolvedBase);
        cache.put(node, schema); // 预放缓存，支持递归 $ref

        if (id != null) {
            idToNode.put(normalizeUri(resolvedBase), node);
        }
        registerAnchor(map.get("$anchor"), node, resolvedBase);
        registerAnchor(map.get("$dynamicAnchor"), node, resolvedBase);

        for (KeywordValidator validator : buildValidators(map, resolvedBase)) {
            schema.add(validator);
        }
        schema.freeze();
        return schema;
    }

    /** 解析 {@code $ref}/{@code $dynamicRef}，返回目标节点编译结果。 */
    public CompiledSchema resolve(String ref, String currentBase) {
        int hash = ref.indexOf('#');
        String basePart = hash >= 0 ? ref.substring(0, hash) : ref;
        String fragment = hash >= 0 ? ref.substring(hash + 1) : "";

        Object target;
        String targetBase;
        if (basePart.isEmpty()) {
            // 同文档引用：JSON Pointer 或 anchor
            if (fragment.startsWith("/")) {
                target = navigatePointer(rootNode, fragment);
            } else if (fragment.isEmpty()) {
                target = rootNode;
            } else {
                target = anchorToNode.get(normalizeUri(currentBase) + "#" + fragment);
            }
            targetBase = currentBase;
        } else {
            String normBase = normalizeUri(resolveUri(currentBase, basePart));
            target = idToNode.get(normBase);
            if (target == null) {
                throw new IllegalArgumentException("未注册的 $id: " + basePart);
            }
            target = resolveFragment(target, fragment, normBase);
            targetBase = normBase;
        }
        if (target == null) {
            throw new IllegalArgumentException("无法解析引用: " + ref);
        }
        return compileNode(target, targetBase);
    }

    // ── 编译期：关键字 → 校验器 ──

    private List<KeywordValidator> buildValidators(Map<String, Object> schema, String baseUri) {
        // $defs 必须先预编译：其 $id/anchor 需要注册，供后续 $ref/$dynamicRef 解析
        if (schema.get("$defs") instanceof Map<?, ?> defs) {
            for (Object child : defs.values()) {
                compileNode(child, baseUri);
            }
        }
        List<KeywordValidator> validators = new ArrayList<>();
        Object ref = schema.get("$ref");
        if (ref instanceof String refStr) {
            validators.add(RefValidator.of(refStr, this, baseUri));
        }
        Object dynamicRef = schema.get("$dynamicRef");
        if (dynamicRef instanceof String dr) {
            validators.add(RefValidator.dynamic(dr, this, baseUri));
        }
        if (schema.containsKey("type")) {
            validators.add(TypeValidator.of(schema.get("type")));
        }
        if (schema.containsKey("enum")) {
            validators.add(EnumValidator.enumOf(schema.get("enum")));
        }
        if (schema.containsKey("const")) {
            validators.add(EnumValidator.constOf(schema.get("const")));
        }
        if (containsAny(schema, "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum", "multipleOf")) {
            validators.add(NumericValidator.of(schema));
        }
        if (containsAny(schema, "minLength", "maxLength", "pattern")) {
            validators.add(StringValidator.of(schema));
        }
        if (schema.containsKey("format")) {
            validators.add(FormatValidator.of(str(schema.get("format"))));
        }
        if (containsAny(schema, "minItems", "maxItems", "uniqueItems", "prefixItems", "items",
                "contains", "minContains", "maxContains")) {
            validators.add(ArrayValidator.of(schema, this, baseUri));
        }
        if (containsAny(schema, "properties", "patternProperties", "additionalProperties", "required",
                "dependentRequired", "dependentSchemas", "propertyNames", "minProperties", "maxProperties")) {
            validators.add(ObjectValidator.of(schema, this, baseUri));
        }
        if (containsAny(schema, "allOf", "anyOf", "oneOf", "not", "if", "then", "else")) {
            validators.add(CombinatorValidator.of(schema, this, baseUri));
        }
        if (containsAny(schema, "unevaluatedProperties", "unevaluatedItems")) {
            validators.add(UnevaluatedValidator.of(schema, this, baseUri));
        }
        return validators;
    }

    // ── 工具 ──

    private void registerAnchor(Object anchorValue, Object node, String baseUri) {
        if (anchorValue instanceof String a && !a.isEmpty()) {
            anchorToNode.put(normalizeUri(baseUri) + "#" + a, node);
        }
    }

    private Object resolveFragment(Object base, String fragment, String baseUri) {
        if (fragment.isEmpty()) {
            return base;
        }
        if (fragment.startsWith("/")) {
            return navigatePointer(base, fragment);
        }
        return anchorToNode.get(normalizeUri(baseUri) + "#" + fragment);
    }

    /** JSON Pointer 导航（空指针返回根，支持 ~0/~1 转义）。 */
    private static Object navigatePointer(Object node, String pointer) {
        if (pointer == null || pointer.isEmpty()) {
            return node;
        }
        if (!pointer.startsWith("/")) {
            return null; // 顶层 anchor 需经注册表解析，这里仅处理 JSON Pointer
        }
        Object cur = node;
        for (String seg : pointer.substring(1).split("/")) {
            String decoded = seg.replace("~1", "/").replace("~0", "~");
            if (cur instanceof Map<?, ?> m) {
                cur = m.get(decoded);
            } else if (cur instanceof List<?> l) {
                try {
                    cur = l.get(Integer.parseInt(decoded));
                } catch (NumberFormatException | IndexOutOfBoundsException e) {
                    return null;
                }
            } else {
                return null;
            }
        }
        return cur;
    }

    /** 相对 URI 解析（简化 RFC 3986：支持绝对、绝对路径、相对拼接）。 */
    public static String resolveUri(String base, String ref) {
        if (ref.isEmpty()) {
            return base;
        }
        if (ref.startsWith("http://") || ref.startsWith("https://") || ref.startsWith("urn:")) {
            return ref;
        }
        if (ref.startsWith("/")) {
            return ref;
        }
        if (base.isEmpty()) {
            return ref;
        }
        int slash = base.lastIndexOf('/');
        return slash >= 0 ? base.substring(0, slash + 1) + ref : base + ref;
    }

    private static String normalizeUri(String uri) {
        int hash = uri.indexOf('#');
        return hash >= 0 ? uri.substring(0, hash) : uri;
    }

    private static String str(Object o) {
        return o instanceof String s ? s : null;
    }

    private static boolean containsAny(Map<String, Object> map, String... keys) {
        for (String k : keys) {
            if (map.containsKey(k)) {
                return true;
            }
        }
        return false;
    }
}
