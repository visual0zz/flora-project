package com.flora.root.mock.jsonschema.impl;

import com.flora.root.mock.jsonschema.JsonGenerationException;
import com.flora.root.mock.regex.automaton.Automaton;
import com.flora.root.mock.regex.automaton.AutomatonException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 生成满足节点硬约束的最小实例（递归截断/硬深度兜底用）。
 * <p>只保证 required/minProperties/minItems/minLength/minimum 等硬约束，
 * 不展开可选部分，也不递归展开 {@code $ref}（最深层以类型最小实例终止）。
 * 有正则的字符串按正则采样，截断层因而仍满足 {@code pattern}。</p>
 */
final class MinimalInstance {

    /** 字符串最小实例的填充长度上限（防超大 minLength）。 */
    private static final int MAX_FILL = 4096;

    private MinimalInstance() {
    }

    static Object of(GenerationNode node, GenerationContext ctx) {
        if (node.alwaysInvalid) {
            throw new JsonGenerationException("false schema 无法生成实例");
        }
        if (node.alwaysValid) {
            return null; // true schema 接受一切，最小实例取 null
        }
        return switch (Nodes.inferType(node.schema, node.patterns())) {
            case "object" -> minimalObject(node, ctx);
            case "array" -> minimalArray(node, ctx);
            case "string" -> minimalString(node, ctx);
            case "integer" -> minimalNumber(node).longValue();
            case "number" -> minimalNumber(node);
            case "boolean" -> Boolean.FALSE;
            default -> null;
        };
    }

    private static Object minimalObject(GenerationNode node, GenerationContext ctx) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String name : Nodes.requiredNames(node.schema)) {
            GenerationNode child = node.propertyNode(name);
            result.put(name, child != null ? of(child, ctx) : null);
        }
        int minProps = Nodes.intOf(node.schema.get("minProperties"), 0);
        int i = 0;
        while (result.size() < minProps) {
            String name = "__p" + i++;
            if (result.containsKey(name)) {
                continue;
            }
            result.put(name, additionalValue(node, ctx, name));
        }
        return result;
    }

    private static Object minimalArray(GenerationNode node, GenerationContext ctx) {
        int minItems = Nodes.intOf(node.schema.get("minItems"), 0);
        int minContains = node.schema.get("contains") instanceof Map
                ? Nodes.intOf(node.schema.get("minContains"), 1) : 0;
        int count = Math.max(minItems, minContains);
        GenerationNode itemNode = node.schema.get("items") instanceof Map itemsMap
                ? node.compiler.compile(itemsMap, node.baseUri) : null;
        List<Object> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(itemNode != null ? of(itemNode, ctx) : ctx.random().randomAlnum(2));
        }
        return result;
    }

    private static String minimalString(GenerationNode node, GenerationContext ctx) {
        int min = Nodes.intOf(node.schema.get("minLength"), 0);
        try {
            Automaton automaton = node.automaton();
            if (automaton != null) {
                return automaton.sample(Math.max(min, automaton.minLength()), ctx.random().random());
            }
        } catch (AutomatonException e) {
            // 正则不受支持：退化为定长填充
        }
        return "a".repeat(Math.min(min, MAX_FILL));
    }

    private static BigDecimal minimalNumber(GenerationNode node) {
        BigDecimal min = Nodes.bound(node.schema.get("minimum"), node.schema.get("exclusiveMinimum"), true);
        if (min == null) {
            min = BigDecimal.ZERO;
        }
        BigDecimal multiple = Nodes.decimalOf(node.schema.get("multipleOf"), null);
        if (multiple != null && multiple.signum() > 0) {
            min = min.divide(multiple, 0, RoundingMode.CEILING).multiply(multiple);
        }
        return min;
    }

    /** minProperties 补位：有 additionalProperties 子 schema 时取其最小实例。 */
    private static Object additionalValue(GenerationNode node, GenerationContext ctx, String name) {
        Object additional = node.schema.get("additionalProperties");
        if (additional instanceof Map) {
            return of(node.compiler.compile(additional, node.baseUri), ctx);
        }
        return "";
    }
}
