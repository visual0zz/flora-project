package com.flora.mock.jsonschema.impl;

import com.flora.mock.regex.RegexStringGenerator;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 节点长度估算器：纯计算、无随机，为预算分配提供参考。
 * <p>递归遍历 schema 估算典型序列化长度；visited 以 schema 对象 identity
 * 防 {@code $ref} 自环，命中回退 6。</p>
 */
public final class LengthEstimator {

    private LengthEstimator() {
    }

    /** 估算节点生成的典型序列化长度。 */
    public static int estimate(GenerationNode node) {
        return estimate(node, new IdentityHashMap<>());
    }

    private static int estimate(GenerationNode node, IdentityHashMap<Object, Boolean> visited) {
        if (node.alwaysInvalid) {
            return 4;
        }
        if (node.alwaysValid) {
            return 6;
        }
        Map<String, Object> schema = node.schema;
        if (schema.containsKey("const")) {
            return estimateJsonValue(schema.get("const"));
        }
        if (schema.get("enum") instanceof List<?> enumValues && !enumValues.isEmpty()) {
            long sum = 0;
            for (Object v : enumValues) {
                sum += estimateJsonValue(v);
            }
            return (int) Math.max(1, sum / enumValues.size());
        }
        if (schema.containsKey("$ref") || schema.containsKey("$dynamicRef")) {
            if (visited.containsKey(schema)) {
                return 6; // 递归引用回退
            }
            visited.put(schema, Boolean.TRUE);
            try {
                GenerationNode target = node.compiler.resolveRef(
                        str(schema.containsKey("$ref") ? schema.get("$ref") : schema.get("$dynamicRef")),
                        node.baseUri);
                return estimate(target, visited);
            } finally {
                visited.remove(schema);
            }
        }
        if (schema.get("anyOf") instanceof List<?> anyOf) {
            return avgBranchEstimate(node, anyOf, visited);
        }
        if (schema.get("oneOf") instanceof List<?> oneOf) {
            return avgBranchEstimate(node, oneOf, visited);
        }
        if (schema.get("if") instanceof Map) {
            int thenEst = schema.get("then") instanceof Map
                    ? estimate(node.compiler.compile(schema.get("then"), node.baseUri), visited) : 6;
            int elseEst = schema.get("else") instanceof Map
                    ? estimate(node.compiler.compile(schema.get("else"), node.baseUri), visited) : 6;
            return (thenEst + elseEst) / 2;
        }
        String type = inferType(schema);
        return switch (type) {
            case "object" -> estimateObject(node, visited);
            case "array" -> estimateArray(node, visited);
            case "string" -> estimateString(node);
            case "integer", "number" -> 6;
            case "boolean" -> 5;
            case "null" -> 4;
            default -> 6;
        };
    }

    private static int avgBranchEstimate(GenerationNode node, List<?> branches,
                                         IdentityHashMap<Object, Boolean> visited) {
        if (branches.isEmpty()) {
            return 6;
        }
        long sum = 0;
        for (Object branch : branches) {
            sum += estimate(node.compiler.compile(branch, node.baseUri), visited);
        }
        return (int) Math.max(1, sum / branches.size());
    }

    private static int estimateObject(GenerationNode node, IdentityHashMap<Object, Boolean> visited) {
        Map<String, Object> schema = node.schema;
        long sum = 0;
        if (schema.get("required") instanceof List<?> required) {
            for (Object r : required) {
                if (r instanceof String name) {
                    GenerationNode child = node.propertyNode(name);
                    int valueEst = child != null ? estimate(child, visited) : 6;
                    sum += valueEst + name.length() + 3;
                }
            }
        }
        if (schema.get("properties") instanceof Map<?, ?> props) {
            for (Object key : props.keySet()) {
                String name = String.valueOf(key);
                if (isRequired(schema, name)) {
                    continue;
                }
                GenerationNode child = node.propertyNode(name);
                int valueEst = child != null ? estimate(child, visited) : 6;
                sum += (valueEst + name.length() + 3) / 2; // 可选属性约半数入选
            }
        }
        // 额外属性按对象规模推算：按总规模一半计入（启发式）
        Object additional = schema.get("additionalProperties");
        if (!(additional instanceof Boolean b && !b) && additional != null) {
            sum += sum / 2;
        }
        return (int) Math.max(1, sum);
    }

    private static int estimateArray(GenerationNode node, IdentityHashMap<Object, Boolean> visited) {
        Map<String, Object> schema = node.schema;
        int itemEst = 6;
        if (schema.get("items") instanceof Map itemsMap) {
            itemEst = Math.max(1, estimate(node.compiler.compile(itemsMap, node.baseUri), visited));
        } else if (schema.get("prefixItems") instanceof List<?> prefix && !prefix.isEmpty()) {
            itemEst = Math.max(1, estimate(node.compiler.compile(prefix.get(0), node.baseUri), visited));
        }
        int min = intOf(schema.get("minItems"), 1);
        int max = intOf(schema.get("maxItems"), 5);
        int avg = Math.max(1, (min + max) / 2);
        return avg * itemEst;
    }

    private static int estimateString(GenerationNode node) {
        Map<String, Object> schema = node.schema;
        if (schema.get("format") instanceof String format) {
            return formatEstimate(format);
        }
        // 多个 pattern 交集：交集语言长度 ≤ 任一分支，取各分支估算最小值
        if (schema.get("_patterns") instanceof List<?> patterns && !patterns.isEmpty()) {
            int min = Integer.MAX_VALUE;
            for (Object p : patterns) {
                if (p instanceof String ps) {
                    min = Math.min(min, RegexStringGenerator.estimateLength(ps));
                }
            }
            if (min != Integer.MAX_VALUE) {
                return Math.max(1, min);
            }
        }
        if (schema.get("pattern") instanceof String pattern) {
            return RegexStringGenerator.estimateLength(pattern);
        }
        int min = intOf(schema.get("minLength"), 0);
        int max = intOf(schema.get("maxLength"), 16);
        return (min + max) / 2 + 1;
    }

    private static int formatEstimate(String format) {
        return switch (format) {
            case "uuid" -> 36;
            case "date-time" -> 20;
            case "email", "idn-email" -> 18;
            case "hostname", "idn-hostname" -> 14;
            case "uri", "iri" -> 20;
            case "ipv4" -> 12;
            case "ipv6" -> 32;
            case "date" -> 10;
            case "time" -> 9;
            case "duration" -> 10;
            default -> 8;
        };
    }

    /** 估算任意 JSON 值（const/enum 元素）的序列化长度。 */
    private static int estimateJsonValue(Object value) {
        if (value == null) {
            return 4;
        }
        if (value instanceof String s) {
            return s.length() + 2;
        }
        if (value instanceof Boolean) {
            return 5;
        }
        if (value instanceof Number) {
            return 6;
        }
        if (value instanceof List<?> list) {
            long sum = 2;
            for (Object item : list) {
                sum += estimateJsonValue(item);
            }
            return (int) Math.min(sum, 4096);
        }
        if (value instanceof Map<?, ?> map) {
            long sum = 2;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                sum += String.valueOf(e.getKey()).length() + 3 + estimateJsonValue(e.getValue());
            }
            return (int) Math.min(sum, 4096);
        }
        return 6;
    }

    private static boolean isRequired(Map<String, Object> schema, String name) {
        return schema.get("required") instanceof List<?> required && required.contains(name);
    }

    /** 无随机地推断类型（供估算使用；type 为列表时取首个）。 */
    static String inferType(Map<String, Object> schema) {
        if (schema.get("type") instanceof String s) {
            return s;
        }
        if (schema.get("type") instanceof List<?> types && !types.isEmpty()) {
            return String.valueOf(types.get(0));
        }
        if (schema.containsKey("properties") || schema.containsKey("patternProperties")
                || schema.containsKey("additionalProperties") || schema.containsKey("required")) {
            return "object";
        }
        if (schema.containsKey("prefixItems") || schema.containsKey("items")
                || schema.containsKey("minItems") || schema.containsKey("maxItems")) {
            return "array";
        }
        if (schema.containsKey("format") || schema.containsKey("pattern")
                || schema.containsKey("_patterns")
                || schema.containsKey("minLength") || schema.containsKey("maxLength")) {
            return "string";
        }
        if (schema.containsKey("minimum") || schema.containsKey("maximum")
                || schema.containsKey("multipleOf")) {
            return "number";
        }
        return "object";
    }

    private static int intOf(Object o, int fallback) {
        return o instanceof Number n ? n.intValue() : fallback;
    }

    private static String str(Object o) {
        return o instanceof String s ? s : null;
    }
}
