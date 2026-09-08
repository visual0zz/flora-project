package com.flora.root.mock.jsonschema.impl;

import com.flora.root.codec.jsonschema.JsonTypes;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * schema 取值与类型推断的公共工具：各类型生成器共用。
 */
final class Nodes {

    private Nodes() {
    }

    /** 取整数值；非数字返回 fallback。 */
    static int intOf(Object o, int fallback) {
        return o instanceof Number n ? n.intValue() : fallback;
    }

    /**
     * 取精确十进制值：走文本转换避免 double 中间表示丢精（{@code 0.01} 等）。
     * 非数字返回 fallback。
     */
    static BigDecimal decimalOf(Object o, BigDecimal fallback) {
        if (o instanceof BigDecimal bd) {
            return bd;
        }
        if (o instanceof Number || o instanceof String) {
            try {
                return new BigDecimal(o.toString());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }

    /** 取字符串；非字符串返回 null。 */
    static String str(Object o) {
        return o instanceof String s ? s : null;
    }

    /**
     * 取边界值：exclusive 优先（2020-12 中 exclusiveMinimum/Maximum 是数值）。
     * exclusive 下界 +1、上界 -1。
     */
    static BigDecimal bound(Object inclusive, Object exclusive, boolean lower) {
        if (exclusive instanceof Number en) {
            BigDecimal v = decimalOf(en, null);
            if (v != null) {
                return lower ? v.add(BigDecimal.ONE) : v.subtract(BigDecimal.ONE);
            }
        }
        return decimalOf(inclusive, null);
    }

    /** 无随机地推断类型（type 为列表时取首个）。 */
    static String inferType(Map<String, Object> schema, List<String> patterns) {
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
        if (schema.containsKey("format") || schema.containsKey("minLength")
                || schema.containsKey("maxLength") || (patterns != null && !patterns.isEmpty())) {
            return "string";
        }
        if (schema.containsKey("minimum") || schema.containsKey("maximum")
                || schema.containsKey("multipleOf")) {
            return "number";
        }
        return "object";
    }

    /** required 属性名（保持声明顺序，去重）。 */
    static Set<String> requiredNames(Map<String, Object> schema) {
        Set<String> required = new LinkedHashSet<>();
        if (schema.get("required") instanceof List<?> requiredList) {
            for (Object r : requiredList) {
                if (r instanceof String s) {
                    required.add(s);
                }
            }
        }
        return required;
    }

    /** properties 的属性名（保持声明顺序）。 */
    static Set<String> propertyNames(Map<String, Object> schema) {
        Set<String> names = new LinkedHashSet<>();
        if (schema.get("properties") instanceof Map<?, ?> props) {
            for (Object key : props.keySet()) {
                names.add(String.valueOf(key));
            }
        }
        return names;
    }

    /** 无 schema 约束时的随机标量。 */
    static Object randomScalar(RandomSupport random) {
        return switch (random.intBetween(0, 3)) {
            case 0 -> random.randomAlnum(random.intBetween(1, 8));
            case 1 -> random.longBetween(0, 1000);
            case 2 -> random.nextBoolean();
            default -> null;
        };
    }

    /** 集合内是否已存在等价值（不存在则加入）。 */
    static boolean uniqueAdd(Set<Object> used, Object item) {
        for (Object existing : used) {
            if (JsonTypes.deepEquals(existing, item)) {
                return false;
            }
        }
        used.add(item);
        return true;
    }
}
