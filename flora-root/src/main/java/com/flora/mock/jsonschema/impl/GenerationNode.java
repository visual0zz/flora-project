package com.flora.mock.jsonschema.impl;

import com.flora.codec.jsonschema.JsonTypes;
import com.flora.mock.jsonschema.JsonGenerationException;
import com.flora.mock.regex.RegexStringGenerator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单节点生成规则。持有原始 schema（已合并 allOf）与编译引用，
 * 运行时按关键字优先级递归生成实例。
 */
public final class GenerationNode {

    final boolean alwaysInvalid;
    final boolean alwaysValid;
    final Map<String, Object> schema;
    final String baseUri;
    final GeneratorCompiler compiler;

    GenerationNode(boolean value, GeneratorCompiler compiler) {
        this.alwaysValid = value;
        this.alwaysInvalid = !value;
        this.schema = null;
        this.baseUri = "";
        this.compiler = compiler;
    }

    GenerationNode(Map<String, Object> schema, String baseUri, GeneratorCompiler compiler) {
        this.alwaysValid = false;
        this.alwaysInvalid = false;
        this.schema = schema;
        this.baseUri = baseUri;
        this.compiler = compiler;
    }

    public Object generate(GenerationContext ctx) {
        if (alwaysInvalid) {
            throw new JsonGenerationException("false schema 无法生成实例");
        }
        if (alwaysValid) {
            return randomScalar(ctx);
        }
        if (schema.containsKey("const")) {
            return schema.get("const");
        }
        if (schema.get("enum") instanceof List<?> enumValues && !enumValues.isEmpty()) {
            return enumValues.get(ctx.random().random().nextInt(enumValues.size()));
        }
        if (schema.containsKey("$ref") || schema.containsKey("$dynamicRef")) {
            GenerationNode target = compiler.resolveRef(
                    str(schema.containsKey("$ref") ? schema.get("$ref") : schema.get("$dynamicRef")), baseUri);
            return target.generate(ctx.deeper());
        }
        if (schema.get("anyOf") instanceof List<?> anyOf) {
            return pickBranch(anyOf, ctx).generate(ctx.deeper());
        }
        if (schema.get("oneOf") instanceof List<?> oneOf) {
            return pickBranch(oneOf, ctx).generate(ctx.deeper());
        }
        if (schema.get("if") instanceof Map) {
            boolean takeThen = ctx.random().nextBoolean();
            Object branch = takeThen ? schema.get("then") : schema.get("else");
            if (branch == null) {
                branch = takeThen ? schema.get("else") : schema.get("then");
            }
            if (branch != null) {
                return compiler.compile(branch, baseUri).generate(ctx.deeper());
            }
        }
        String type = pickType(ctx);
        return generateByType(type, ctx);
    }

    // ── 类型生成 ──

    private Object generateByType(String type, GenerationContext ctx) {
        if (ctx.depth() >= ctx.config().maxDepth()) {
            return minimalOfType(type);
        }
        return switch (type) {
            case "object" -> generateObject(ctx);
            case "array" -> generateArray(ctx);
            case "string" -> generateString(ctx);
            case "integer" -> generateNumber(ctx, true);
            case "number" -> generateNumber(ctx, false);
            case "boolean" -> ctx.random().nextBoolean();
            case "null" -> null;
            default -> randomScalar(ctx);
        };
    }

    private Object generateObject(GenerationContext ctx) {
        Map<String, Object> result = new LinkedHashMap<>();
        // 必填属性
        Set<String> toGenerate = new LinkedHashSet<>();
        if (schema.get("required") instanceof List<?> required) {
            for (Object r : required) {
                if (r instanceof String s) {
                    toGenerate.add(s);
                }
            }
        }
        // 可选属性随机选取
        if (schema.get("properties") instanceof Map<?, ?> props) {
            for (Object key : props.keySet()) {
                if (ctx.random().nextBoolean() || toGenerate.contains(String.valueOf(key))) {
                    toGenerate.add(String.valueOf(key));
                }
            }
        }
        // 按估算权重瓜分预算：est_i = valueEst + 属性名长度 + 开销
        Map<String, Integer> weight = new LinkedHashMap<>();
        long total = 0;
        for (String name : toGenerate) {
            GenerationNode node = propertyNode(name);
            int valueEst = node != null ? LengthEstimator.estimate(node) : 6;
            int est = valueEst + name.length() + 3;
            weight.put(name, est);
            total += est;
        }
        for (String name : toGenerate) {
            GenerationNode node = propertyNode(name);
            int childBudget = total == 0 ? 8 : (int) (ctx.budget() * weight.get(name) / total);
            result.put(name, node != null ? node.generate(ctx.deeper(childBudget)) : null);
        }
        // dependentRequired 补依赖
        if (schema.get("dependentRequired") instanceof Map<?, ?> deps) {
            for (Map.Entry<?, ?> e : deps.entrySet()) {
                String trigger = String.valueOf(e.getKey());
                if (result.containsKey(trigger) && e.getValue() instanceof List<?> needList) {
                    for (Object need : needList) {
                        if (need instanceof String n && !result.containsKey(n)) {
                            GenerationNode node = propertyNode(n);
                            int childBudget = Math.max(8, ctx.budget() / Math.max(1, needList.size()));
                            result.put(n, node != null ? node.generate(ctx.deeper(childBudget)) : null);
                        }
                    }
                }
            }
        }
        // patternProperties：为每个 pattern 生成 0..1 个匹配属性
        if (schema.get("patternProperties") instanceof Map<?, ?> patterns) {
            for (Map.Entry<?, ?> e : patterns.entrySet()) {
                String pattern = String.valueOf(e.getKey());
                if (ctx.random().nextBoolean()) {
                    String name = ctx.random().randomAlpha(4);
                    GenerationNode node = compiler.compile(e.getValue(), baseUri);
                    int childBudget = Math.max(8, ctx.budget() / Math.max(1, patterns.size()));
                    result.put(name, node.generate(ctx.deeper(childBudget)));
                }
            }
        }
        // additionalProperties：额外属性作为可变部分参与预算分配
        // 数量上限 ≈ 剩余预算 / 单个额外属性的估算长度，元素短则多补、长则少补
        Object additional = schema.get("additionalProperties");
        if (!(additional instanceof Boolean b && !b) && additional != null) {
            int extraEst = 6;
            if (additional instanceof Map) {
                extraEst = Math.max(1, LengthEstimator.estimate(compiler.compile(additional, baseUri)));
            }
            int overhead = "extra".length() + 3 + 2; // 属性名前缀 + 随机后缀 + 冒号/逗号开销
            int cap = Math.max(0, ctx.budget() / Math.max(1, extraEst + overhead));
            int extra = ctx.random().intBetween(0, cap);
            for (int i = 0; i < extra; i++) {
                String name = "extra" + ctx.random().randomAlpha(3);
                if (!result.containsKey(name)) {
                    GenerationNode node = additional instanceof Map
                            ? compiler.compile(additional, baseUri) : null;
                    int childBudget = Math.max(8, ctx.budget() / Math.max(1, extra));
                    result.put(name, node != null ? node.generate(ctx.deeper(childBudget))
                            : ctx.random().randomAlpha(4));
                }
            }
        }
        return result;
    }

    private Object generateArray(GenerationContext ctx) {
        List<Object> result = new ArrayList<>();
        Set<Object> used = new LinkedHashSet<>();
        int min = intOf(schema.get("minItems"), 0);
        // 元素长→重复少，元素短→重复多：count ≈ budget / itemEst
        int itemEst = 6;
        if (schema.get("items") instanceof Map itemsMap) {
            itemEst = Math.max(1, LengthEstimator.estimate(compiler.compile(itemsMap, baseUri)));
        } else if (schema.get("prefixItems") instanceof List<?> prefix && !prefix.isEmpty()) {
            itemEst = Math.max(1, LengthEstimator.estimate(compiler.compile(prefix.get(0), baseUri)));
        }
        int computedMax = Math.max(min, (ctx.budget() - 2) / (itemEst + 1));
        int max = intOf(schema.get("maxItems"), computedMax);
        max = Math.min(max, 64); // 防溢出
        if (max < min) {
            max = min;
        }
        int length = ctx.random().intBetween(min, max);
        int perItem = length > 0 ? Math.max(1, ctx.budget() / length) : ctx.budget();
        // prefixItems 元组
        if (schema.get("prefixItems") instanceof List<?> prefix) {
            for (int i = 0; i < prefix.size() && result.size() < length; i++) {
                result.add(compiler.compile(prefix.get(i), baseUri).generate(ctx.deeper(perItem)));
            }
        }
        // contains 至少一个
        if (schema.get("contains") instanceof Map && result.size() < length) {
            GenerationNode contains = compiler.compile(schema.get("contains"), baseUri);
            result.add(contains.generate(ctx.deeper(perItem)));
        }
        // items 填充剩余
        boolean unique = Boolean.TRUE.equals(schema.get("uniqueItems"));
        if (schema.get("items") instanceof Map itemsMap) {
            GenerationNode itemsNode = compiler.compile(itemsMap, baseUri);
            while (result.size() < length) {
                Object item = itemsNode.generate(ctx.deeper(perItem));
                if (unique && !uniqueAdd(used, item)) {
                    continue;
                }
                result.add(item);
            }
        }
        // 无 items：随机填充
        while (result.size() < length) {
            Object item = randomScalar(ctx);
            if (unique && !uniqueAdd(used, item)) {
                continue;
            }
            result.add(item);
        }
        return result;
    }

    private Object generateString(GenerationContext ctx) {
        // format 优先
        if (schema.get("format") instanceof String format) {
            return new FormatGenerator(ctx.random()).generate(format);
        }
        int min = intOf(schema.get("minLength"), 0);
        int max = intOf(schema.get("maxLength"), ctx.budget());
        if (max < min) {
            max = min;
        }
        int target = clamp(ctx.budget(), min, max);
        // pattern 逆向：目标长度传入 regex，可变长量词朝其靠拢（硬约束由量词自身保证）
        if (schema.get("pattern") instanceof String pattern) {
            return RegexStringGenerator.of(pattern, ctx.random().random()).generate(target);
        }
        // 自由字符串：target ± 20% 扰动后 clamp 到长度区间
        int len = target;
        if (max > min) {
            int delta = Math.max(1, target / 5);
            len = clamp(target - delta + ctx.random().intBetween(0, 2 * delta), min, max);
        }
        return ctx.random().randomAlnum(len);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    private Object generateNumber(GenerationContext ctx, boolean integer) {
        BigDecimal min = bound(schema.get("minimum"), schema.get("exclusiveMinimum"), true);
        BigDecimal max = bound(schema.get("maximum"), schema.get("exclusiveMaximum"), false);
        if (min == null) {
            min = BigDecimal.ZERO;
        }
        if (max == null) {
            max = min.add(BigDecimal.valueOf(1000));
        }
        BigDecimal value = integer
                ? BigDecimal.valueOf(ctx.random().longBetween(min.longValue(), max.longValue()))
                : ctx.random().decimalBetween(min, max);
        // multipleOf 对齐
        if (schema.get("multipleOf") instanceof Number m && m.doubleValue() > 0) {
            BigDecimal mult = BigDecimal.valueOf(m.doubleValue());
            value = value.divideToIntegralValue(mult).multiply(mult);
        }
        if (integer) {
            return value.longValue();
        }
        return value;
    }

    // ── 工具 ──

    GenerationNode propertyNode(String name) {
        if (schema.get("properties") instanceof Map<?, ?> props) {
            Object node = props.get(name);
            if (node != null) {
                return compiler.compile(node, baseUri);
            }
        }
        // patternProperties 兜底
        if (schema.get("patternProperties") instanceof Map<?, ?> patterns) {
            for (Map.Entry<?, ?> e : patterns.entrySet()) {
                if (String.valueOf(e.getKey()).matches(".*")) {
                    return compiler.compile(e.getValue(), baseUri);
                }
            }
        }
        // additionalProperties 兜底
        if (schema.get("additionalProperties") instanceof Map additional) {
            return compiler.compile(additional, baseUri);
        }
        return null;
    }

    private GenerationNode pickBranch(List<?> branches, GenerationContext ctx) {
        int idx = ctx.random().random().nextInt(branches.size());
        return compiler.compile(branches.get(idx), baseUri);
    }

    private String pickType(GenerationContext ctx) {
        if (schema.get("type") instanceof String s) {
            return s;
        }
        if (schema.get("type") instanceof List<?> types) {
            List<String> list = new ArrayList<>();
            for (Object t : types) {
                if (t instanceof String s) {
                    list.add(s);
                }
            }
            return list.isEmpty() ? "object" : list.get(ctx.random().random().nextInt(list.size()));
        }
        // 关键字推断
        if (schema.containsKey("properties") || schema.containsKey("patternProperties")
                || schema.containsKey("additionalProperties") || schema.containsKey("required")) {
            return "object";
        }
        if (schema.containsKey("prefixItems") || schema.containsKey("items")
                || schema.containsKey("minItems") || schema.containsKey("maxItems")) {
            return "array";
        }
        if (schema.containsKey("format") || schema.containsKey("pattern")
                || schema.containsKey("minLength") || schema.containsKey("maxLength")) {
            return "string";
        }
        if (schema.containsKey("minimum") || schema.containsKey("maximum")
                || schema.containsKey("multipleOf")) {
            return "number";
        }
        return "object";
    }

    private Object minimalOfType(String type) {
        return switch (type) {
            case "object" -> new LinkedHashMap<String, Object>();
            case "array" -> new ArrayList<Object>();
            case "string" -> "";
            case "integer" -> 0L;
            case "number" -> BigDecimal.ZERO;
            case "boolean" -> Boolean.FALSE;
            case "null" -> null;
            default -> null;
        };
    }

    private Object randomScalar(GenerationContext ctx) {
        return switch (ctx.random().intBetween(0, 3)) {
            case 0 -> ctx.random().randomAlnum(ctx.random().intBetween(1, 8));
            case 1 -> ctx.random().longBetween(0, 1000);
            case 2 -> ctx.random().nextBoolean();
            default -> null;
        };
    }

    private boolean uniqueAdd(Set<Object> used, Object item) {
        for (Object existing : used) {
            if (JsonTypes.deepEquals(existing, item)) {
                return false;
            }
        }
        used.add(item);
        return true;
    }

    private static BigDecimal bound(Object inclusive, Object exclusive, boolean lower) {
        if (exclusive != null && exclusive instanceof Number en) {
            BigDecimal v = BigDecimal.valueOf(((Number) exclusive).doubleValue());
            return lower ? v.add(BigDecimal.ONE) : v.subtract(BigDecimal.ONE);
        }
        return inclusive instanceof Number n ? BigDecimal.valueOf(((Number) n).doubleValue()) : null;
    }

    private static int intOf(Object o, int fallback) {
        return o instanceof Number n ? n.intValue() : fallback;
    }

    private static String str(Object o) {
        return o instanceof String s ? s : null;
    }
}
