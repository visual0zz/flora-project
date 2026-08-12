package com.flora.root.codec.jsonschema.impl;

import com.flora.root.codec.json.model.JsonArray;
import com.flora.root.codec.json.model.JsonBool;
import com.flora.root.codec.json.model.JsonObject;
import com.flora.root.codec.json.model.JsonValue;
import com.flora.root.codec.jsonschema.validator.ArrayValidator;
import com.flora.root.codec.jsonschema.validator.CombinatorValidator;
import com.flora.root.codec.jsonschema.validator.EnumValidator;
import com.flora.root.codec.jsonschema.validator.FormatValidator;
import com.flora.root.codec.jsonschema.validator.KeywordValidator;
import com.flora.root.codec.jsonschema.validator.NumericValidator;
import com.flora.root.codec.jsonschema.validator.ObjectValidator;
import com.flora.root.codec.jsonschema.validator.RefValidator;
import com.flora.root.codec.jsonschema.validator.StringValidator;
import com.flora.root.codec.jsonschema.validator.TypeValidator;
import com.flora.root.codec.jsonschema.validator.UnevaluatedValidator;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * schema 编译与引用解析注册表。
 * <p>编译期：记忆化编译每个 schema 节点，处理 {@code $id}（注册）、
 * {@code $anchor}/{@code $dynamicAnchor}（注册）、递归编译子 schema 与 {@code $defs}。
 * schema 节点统一以 {@link JsonObject} 表达，校验器基于其类型安全取值助手工作。
 * 解析期：{@code $ref}/{@code $dynamicRef} 经 JSON Pointer / anchor / {@code $id} 定位并编译目标。
 * 节点先入缓存，支持递归引用的循环防护。
 * <p>引用解析（{@link #resolveNode}）返回原始节点对象（{@link JsonObject} 或 {@link JsonBool}），
 * 供数据生成子系统复用。</p>
 */
public final class SchemaRegistry {

    private final Map<Object, CompiledSchema> cache = new IdentityHashMap<>();
    private final Map<String, JsonValue> idToNode = new java.util.HashMap<>();
    private final Map<String, JsonValue> anchorToNode = new java.util.HashMap<>();
    private final JsonValue rootNode;
    private CompiledSchema rootSchema;

    private SchemaRegistry(JsonValue rootNode) {
        this.rootNode = rootNode;
    }

    /** 编译根 schema 并创建注册表。 */
    public static SchemaRegistry of(JsonValue rootNode) {
        SchemaRegistry reg = new SchemaRegistry(rootNode);
        reg.rootSchema = reg.compileNode(rootNode, "");
        return reg;
    }

    public CompiledSchema root() {
        return rootSchema;
    }

    /** 记忆化编译节点。布尔 schema 编译为恒真/恒假。
     * 仅接受 {@link JsonObject} 或 {@code Boolean} 作为 schema 节点（裸 {@code Map} 不被接受）；
     * 缓存按原始节点对象（IdentityHashMap）键控以支持递归引用的循环防护。 */
    public CompiledSchema compileNode(JsonValue node, String baseUri) {
        if (node instanceof JsonBool b) {
            CompiledSchema result = b.value() ? CompiledSchema.always() : CompiledSchema.never();
            cache.put(node, result);
            return result;
        }
        if (!(node instanceof JsonObject obj)) {
            throw new IllegalArgumentException("schema 节点必须是 JsonObject 或 Boolean，实际为: "
                    + (node == null ? "null" : node.getClass().getName()));
        }
        CompiledSchema cached = cache.get(node);
        if (cached != null) {
            return cached;
        }
        String id = obj.getString("$id");
        String resolvedBase = id != null ? resolveUri(baseUri, id) : baseUri;
        CompiledSchema schema = CompiledSchema.newSchema(resolvedBase);
        cache.put(node, schema); // 预放缓存，支持递归 $ref

        if (id != null) {
            idToNode.put(normalizeUri(resolvedBase), node);
        }
        registerAnchor(obj.get("$anchor"), node, resolvedBase);
        registerAnchor(obj.get("$dynamicAnchor"), node, resolvedBase);

        for (KeywordValidator validator : buildValidators(obj, resolvedBase)) {
            schema.add(validator);
        }
        schema.freeze();
        return schema;
    }

    /** 解析 {@code $ref}/{@code $dynamicRef}，返回目标节点编译结果。 */
    public CompiledSchema resolve(String ref, String currentBase) {
        ResolvedTarget target = locate(ref, currentBase);
        if (target.node() == null) {
            throw new IllegalArgumentException("无法解析引用: " + ref);
        }
        return compileNode(target.node(), target.base());
    }

    /** 解析引用，返回目标原始 schema 节点（供生成器等复用）。 */
    public JsonValue resolveNode(String ref, String currentBase) {
        ResolvedTarget target = locate(ref, currentBase);
        if (target.node() == null) {
            throw new IllegalArgumentException("无法解析引用: " + ref);
        }
        return target.node();
    }

    private record ResolvedTarget(JsonValue node, String base) {
    }

    private ResolvedTarget locate(String ref, String currentBase) {
        int hash = ref.indexOf('#');
        String basePart = hash >= 0 ? ref.substring(0, hash) : ref;
        String fragment = hash >= 0 ? ref.substring(hash + 1) : "";

        if (basePart.isEmpty()) {
            // 同文档引用：JSON Pointer 或 anchor
            if (fragment.startsWith("/")) {
                return new ResolvedTarget(navigatePointer(rootNode, fragment), currentBase);
            }
            if (fragment.isEmpty()) {
                return new ResolvedTarget(rootNode, currentBase);
            }
            return new ResolvedTarget(anchorToNode.get(normalizeUri(currentBase) + "#" + fragment), currentBase);
        }
        String normBase = normalizeUri(resolveUri(currentBase, basePart));
        JsonValue target = idToNode.get(normBase);
        if (target == null) {
            return new ResolvedTarget(null, normBase);
        }
        target = resolveFragment(target, fragment, normBase);
        return new ResolvedTarget(target, normBase);
    }

    // ── 编译期：关键字 → 校验器 ──

    private List<KeywordValidator> buildValidators(JsonObject schema, String baseUri) {
        // $defs 必须先预编译：其 $id/anchor 需要注册，供后续 $ref/$dynamicRef 解析
        JsonObject defs = schema.getObject("$defs");
        if (defs != null) {
            for (JsonValue child : defs.values()) {
                compileNode(child, baseUri);
            }
        }
        List<KeywordValidator> validators = new ArrayList<>();
        String ref = schema.getString("$ref");
        if (ref != null) {
            validators.add(RefValidator.of(ref, this, baseUri));
        }
        String dynamicRef = schema.getString("$dynamicRef");
        if (dynamicRef != null) {
            validators.add(RefValidator.dynamic(dynamicRef, this, baseUri));
        }
        if (schema.containsKey("type")) {
            validators.add(TypeValidator.of(schema.get("type").toNative()));
        }
        if (schema.containsKey("enum")) {
            JsonValue ev = schema.get("enum");
            validators.add(EnumValidator.enumOf(ev.toNative()));
        }
        if (schema.containsKey("const")) {
            validators.add(EnumValidator.constOf(schema.get("const").toNative()));
        }
        if (containsAny(schema, "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum", "multipleOf")) {
            validators.add(NumericValidator.of(schema));
        }
        if (containsAny(schema, "minLength", "maxLength", "pattern")) {
            validators.add(StringValidator.of(schema));
        }
        if (schema.containsKey("format")) {
            validators.add(FormatValidator.of(schema.getString("format")));
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

    private void registerAnchor(JsonValue anchorValue, JsonValue node, String baseUri) {
        if (anchorValue != null && anchorValue.isString()) {
            String a = anchorValue.asString();
            if (!a.isEmpty()) {
                anchorToNode.put(normalizeUri(baseUri) + "#" + a, node);
            }
        }
    }

    private JsonValue resolveFragment(JsonValue base, String fragment, String baseUri) {
        if (fragment.isEmpty()) {
            return base;
        }
        if (fragment.startsWith("/")) {
            return navigatePointer(base, fragment);
        }
        return anchorToNode.get(normalizeUri(baseUri) + "#" + fragment);
    }

    /** JSON Pointer 导航（空指针返回根，支持 ~0/~1 转义）。
     * 仅处理 {@link JsonObject}/{@link JsonArray} 节点（裸 Map/List 原生树不被接受）。 */
    private static JsonValue navigatePointer(JsonValue node, String pointer) {
        if (pointer == null || pointer.isEmpty()) {
            return node;
        }
        if (!pointer.startsWith("/")) {
            return null; // 顶层 anchor 需经注册表解析，这里仅处理 JSON Pointer
        }
        JsonValue cur = node;
        for (String seg : pointer.substring(1).split("/")) {
            String decoded = seg.replace("~1", "/").replace("~0", "~");
            if (cur instanceof JsonObject jo) {
                cur = jo.get(decoded);
            } else if (cur instanceof JsonArray ja) {
                try {
                    cur = ja.get(Integer.parseInt(decoded));
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

    private static boolean containsAny(JsonObject obj, String... keys) {
        for (String k : keys) {
            if (obj.containsKey(k)) {
                return true;
            }
        }
        return false;
    }
}
