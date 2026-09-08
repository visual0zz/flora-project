package com.flora.root.mock.jsonschema.impl;

import com.flora.root.mock.regex.RegexStringGenerator;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * object 节点生成：必填属性必生成，可选部分按随深度递减的展开概率取舍。
 * <ul>
 *   <li>{@code patternProperties}：属性名按该 pattern 生成，保证落在约束内。</li>
 *   <li>{@code minProperties}：不足时按 {@code additionalProperties} 的子 schema 补值，
 *       而不是塞空串（空串常与子 schema 冲突）。</li>
 * </ul>
 */
final class ObjectGenerator {

    /** additionalProperties 单次最多补充的额外属性数。 */
    private static final int MAX_EXTRA_PROPERTIES = 2;

    private ObjectGenerator() {
    }

    static Object generate(GenerationNode node, GenerationContext ctx) {
        Map<String, Object> schema = node.schema;
        Map<String, Object> result = new LinkedHashMap<>();

        Set<String> required = Nodes.requiredNames(schema);
        Set<String> toGenerate = new LinkedHashSet<>(required);
        for (String name : Nodes.propertyNames(schema)) {
            if (required.contains(name) || ctx.shouldExpand()) {
                toGenerate.add(name);
            }
        }
        for (String name : toGenerate) {
            GenerationNode child = node.propertyNode(name);
            result.put(name, child != null ? child.generate(ctx.deeper(name)) : null);
        }
        fillDependentRequired(node, ctx, result);
        fillPatternProperties(node, ctx, result);
        fillAdditionalProperties(node, ctx, result);
        fillToMinProperties(node, ctx, result);
        return result;
    }

    /** dependentRequired：触发属性已在结果中时补齐其依赖属性。 */
    private static void fillDependentRequired(GenerationNode node, GenerationContext ctx,
                                              Map<String, Object> result) {
        if (!(node.schema.get("dependentRequired") instanceof Map<?, ?> deps)) {
            return;
        }
        for (Map.Entry<?, ?> e : deps.entrySet()) {
            String trigger = String.valueOf(e.getKey());
            if (!result.containsKey(trigger) || !(e.getValue() instanceof List<?> needList)) {
                continue;
            }
            for (Object need : needList) {
                if (need instanceof String name && !result.containsKey(name)) {
                    GenerationNode child = node.propertyNode(name);
                    result.put(name, child != null ? child.generate(ctx.deeper(name)) : null);
                }
            }
        }
    }

    /** patternProperties：每个 pattern 按深度概率生成一个匹配该 pattern 的属性名。 */
    private static void fillPatternProperties(GenerationNode node, GenerationContext ctx,
                                              Map<String, Object> result) {
        if (!(node.schema.get("patternProperties") instanceof Map<?, ?> patterns)) {
            return;
        }
        for (Map.Entry<?, ?> e : patterns.entrySet()) {
            if (!ctx.shouldExpand()) {
                continue;
            }
            String name = nameMatching(Nodes.str(e.getKey()), ctx);
            if (name.isEmpty() || result.containsKey(name)) {
                continue;
            }
            GenerationNode child = node.compiler.compile(e.getValue(), node.baseUri);
            result.put(name, child.generate(ctx.deeper(name)));
        }
    }

    /** additionalProperties：额外属性数量取固定小范围。 */
    private static void fillAdditionalProperties(GenerationNode node, GenerationContext ctx,
                                                 Map<String, Object> result) {
        Object additional = node.schema.get("additionalProperties");
        if (additional == null || Boolean.FALSE.equals(additional)) {
            return;
        }
        int extra = ctx.shouldExpand() ? ctx.random().intBetween(1, MAX_EXTRA_PROPERTIES) : 0;
        for (int i = 0; i < extra; i++) {
            String name = "extra" + ctx.random().randomAlpha(3);
            if (result.containsKey(name)) {
                continue;
            }
            result.put(name, additional instanceof Map
                    ? node.compiler.compile(additional, node.baseUri).generate(ctx.deeper(name))
                    : ctx.random().randomAlnum(4));
        }
    }

    /** minProperties：属性数不足时按 additionalProperties 的子 schema 补值。 */
    private static void fillToMinProperties(GenerationNode node, GenerationContext ctx,
                                            Map<String, Object> result) {
        int minProps = Nodes.intOf(node.schema.get("minProperties"), 0);
        int i = 0;
        while (result.size() < minProps) {
            String name = "__p" + i++;
            if (!result.containsKey(name)) {
                result.put(name, filler(node, ctx, name));
            }
        }
    }

    /** 补位属性的取值：有 additionalProperties 子 schema 则按其生成，否则随机短串。 */
    static Object filler(GenerationNode node, GenerationContext ctx, String name) {
        Object additional = node.schema.get("additionalProperties");
        if (additional instanceof Map) {
            return node.compiler.compile(additional, node.baseUri).generate(ctx.deeper(name));
        }
        if (Boolean.FALSE.equals(additional)) {
            // 与 minProperties 冲突：schema 自身矛盾，尽力填空串
            return "";
        }
        return ctx.random().randomAlnum(4);
    }

    /** 生成匹配 pattern 的属性名；pattern 不支持时退化为随机名。 */
    private static String nameMatching(String pattern, GenerationContext ctx) {
        if (pattern == null) {
            return ctx.random().randomAlpha(4);
        }
        try {
            String name = RegexStringGenerator.of(pattern, ctx.random().random()).generate(6);
            return name.isEmpty() ? ctx.random().randomAlpha(4) : name;
        } catch (RuntimeException e) {
            return ctx.random().randomAlpha(4);
        }
    }
}
