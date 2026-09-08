package com.flora.root.mock.jsonschema.impl;

import com.flora.root.codec.jsonschema.JsonTypes;
import com.flora.root.mock.regex.automaton.Automaton;
import com.flora.root.mock.regex.automaton.AutomatonException;
import com.flora.root.mock.jsonschema.JsonGenerationException;
import com.flora.root.mock.regex.RegexStringGenerator;

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

    /** 纯防溢出保险：递归深度绝对上限（正常由随深度递减的展开概率收敛，几乎不可能触发）。 */
    private static final int HARD_DEPTH_LIMIT = 1000;
    /** additionalProperties 单次最多补充的额外属性数。 */
    private static final int MAX_EXTRA_PROPERTIES = 2;
    /** 无 maxItems 时，数组长度在 minItems 之上追加的额度。 */
    private static final int DEFAULT_ARRAY_SPAN = 3;
    /** 数组长度硬上限（防溢出；schema 声明更大的 maxItems 时以本值为准）。 */
    private static final int HARD_MAX_ITEMS = 32;

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
            if (ctx.onPath(target.schema)) {
                // 循环引用：随深度递减的概率决定是否继续展开（越深越可能截断）
                if (ctx.shouldExpand()) {
                    return expandRecursive(target, ctx);
                }
                return minimalSatisfying(target, ctx);
            }
            return expandRecursive(target, ctx);
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

    // ── 递归展开 ──

    /** 展开 $ref 目标节点：加入路径防循环检测，生成后移除（异常时也保证退出）。 */
    private Object expandRecursive(GenerationNode target, GenerationContext ctx) {
        ctx.enterPath(target.schema);
        try {
            return target.generate(ctx.deeper());
        } finally {
            ctx.exitPath(target.schema);
        }
    }

    /** 从节点 schema 推断类型（供递归截断时返回最小实例）。 */
    private static String typeOf(GenerationNode node) {
        return inferType(node.schema);
    }

    // ── 类型生成 ──

    private Object generateByType(String type, GenerationContext ctx) {
        // 纯防溢出保险：正常由随深度递减的展开概率收敛，几乎不可能触发
        if (ctx.depth() >= HARD_DEPTH_LIMIT) {
            return minimalSatisfying(this, ctx);
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
        Set<String> required = new LinkedHashSet<>();
        if (schema.get("required") instanceof List<?> requiredList) {
            for (Object r : requiredList) {
                if (r instanceof String s) {
                    required.add(s);
                }
            }
        }
        Set<String> toGenerate = new LinkedHashSet<>(required);
        // 可选属性：按随深度递减的概率决定是否展开这一层
        if (schema.get("properties") instanceof Map<?, ?> props) {
            for (Object key : props.keySet()) {
                String name = String.valueOf(key);
                if (required.contains(name) || ctx.shouldExpand()) {
                    toGenerate.add(name);
                }
            }
        }
        for (String name : toGenerate) {
            GenerationNode node = propertyNode(name);
            result.put(name, node != null ? node.generate(ctx.deeper(name)) : null);
        }
        // dependentRequired 补依赖
        if (schema.get("dependentRequired") instanceof Map<?, ?> deps) {
            for (Map.Entry<?, ?> e : deps.entrySet()) {
                String trigger = String.valueOf(e.getKey());
                if (result.containsKey(trigger) && e.getValue() instanceof List<?> needList) {
                    for (Object need : needList) {
                        if (need instanceof String n && !result.containsKey(n)) {
                            GenerationNode node = propertyNode(n);
                            result.put(n, node != null ? node.generate(ctx.deeper(n)) : null);
                        }
                    }
                }
            }
        }
        // patternProperties：每个 pattern 按深度概率生成 0..1 个匹配属性
        if (schema.get("patternProperties") instanceof Map<?, ?> patterns) {
            for (Map.Entry<?, ?> e : patterns.entrySet()) {
                if (!ctx.shouldExpand()) {
                    continue;
                }
                String name = ctx.random().randomAlpha(4);
                GenerationNode node = compiler.compile(e.getValue(), baseUri);
                result.put(name, node.generate(ctx.deeper(name)));
            }
        }
        // additionalProperties：额外属性数量取固定小范围，不再由长度预算推算
        Object additional = schema.get("additionalProperties");
        if (!(additional instanceof Boolean b && !b) && additional != null) {
            int extra = ctx.shouldExpand()
                    ? ctx.random().intBetween(1, MAX_EXTRA_PROPERTIES) : 0;
            for (int i = 0; i < extra; i++) {
                String name = "extra" + ctx.random().randomAlpha(3);
                if (!result.containsKey(name)) {
                    GenerationNode node = additional instanceof Map
                            ? compiler.compile(additional, baseUri) : null;
                    result.put(name, node != null ? node.generate(ctx.deeper(name))
                            : ctx.random().randomAlpha(4));
                }
            }
        }
        // minProperties：属性数不足时补足（超出部分用空值占位）
        int minProps = intOf(schema.get("minProperties"), 0);
        int i = 0;
        while (result.size() < minProps) {
            String name = "__p" + i++;
            if (!result.containsKey(name)) {
                result.put(name, "");
            }
        }
        return result;
    }

    private Object generateArray(GenerationContext ctx) {
        List<Object> result = new ArrayList<>();
        Set<Object> used = new LinkedHashSet<>();
        int min = intOf(schema.get("minItems"), 0);
        // 长度不再由预算推算：默认在 minItems 之上追加固定额度，受 maxItems 与硬上限约束
        int max = intOf(schema.get("maxItems"), min + DEFAULT_ARRAY_SPAN);
        max = Math.min(max, HARD_MAX_ITEMS); // 防溢出
        if (max < min) {
            max = min;
        }
        int length = ctx.random().intBetween(min, max);
        // prefixItems 元组
        if (schema.get("prefixItems") instanceof List<?> prefix) {
            for (int i = 0; i < prefix.size() && result.size() < length; i++) {
                result.add(compiler.compile(prefix.get(i), baseUri).generate(ctx.deeper()));
            }
        }
        // contains 至少一个
        if (schema.get("contains") instanceof Map && result.size() < length) {
            GenerationNode contains = compiler.compile(schema.get("contains"), baseUri);
            result.add(contains.generate(ctx.deeper()));
        }
        // items 填充剩余
        boolean unique = Boolean.TRUE.equals(schema.get("uniqueItems"));
        if (schema.get("items") instanceof Map itemsMap) {
            GenerationNode itemsNode = compiler.compile(itemsMap, baseUri);
            while (result.size() < length) {
                Object item = itemsNode.generate(ctx.deeper());
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

    /**
     * 字符串生成：先按字段名猜语义（有 {@code format} 时优先按 format）造一个"像样"的值，
     * 校验其是否满足本节点的 {@code pattern} 与长度约束；不合规则重试，
     * 累计 {@link SemanticStringGenerator#MAX_REJECTIONS} 次被拒后放弃语义生成，
     * 改为直接用对应正则调用 {@link RegexStringGenerator} 生成。
     */
    private Object generateString(GenerationContext ctx) {
        List<String> patterns = patterns();
        int min = intOf(schema.get("minLength"), 0);
        int max = intOf(schema.get("maxLength"), -1); // -1 表示无上界
        for (int attempt = 0; attempt < SemanticStringGenerator.MAX_REJECTIONS; attempt++) {
            String candidate = candidateString(ctx);
            if (fits(candidate, patterns, min, max)) {
                return candidate;
            }
        }
        // 语义候选连续被拒 → 改为按正则直接生成（无正则时按长度区间造随机串）
        if (!patterns.isEmpty()) {
            return generateByPatterns(patterns, ctx, min, max);
        }
        return randomStringInRange(ctx, min, max);
    }

    /** 语义候选值：{@code format} 优先，其次按属性名猜测字段含义生成。 */
    private String candidateString(GenerationContext ctx) {
        if (schema.get("format") instanceof String format) {
            return new FormatGenerator(ctx.random()).generate(format);
        }
        return new SemanticStringGenerator(ctx.random()).generate(ctx.name());
    }

    /** 候选值是否合规：长度落在区间内，且命中全部 pattern。 */
    private static boolean fits(String value, List<String> patterns, int min, int max) {
        if (value == null) {
            return false;
        }
        if (value.length() < min || (max >= 0 && value.length() > max)) {
            return false;
        }
        for (String p : patterns) {
            if (!matchesPattern(p, value)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 正则命中判定：与校验侧一致走 JDK {@code java.util.regex} 的 {@code find()}。
     * 校验侧无法编译的正则视为不合规（交由回退路径处理）。
     */
    private static boolean matchesPattern(String pattern, String value) {
        try {
            return java.util.regex.Pattern.compile(pattern).matcher(value).find();
        } catch (java.util.regex.PatternSyntaxException e) {
            return false;
        }
    }

    /** 回退路径：直接用正则生成（多个 pattern 走自动机交集）。 */
    private Object generateByPatterns(List<String> patterns, GenerationContext ctx, int min, int max) {
        int target = targetLengthFor(min, max);
        if (patterns.size() > 1) {
            Automaton combined = null;
            for (String ps : patterns) {
                try {
                    Automaton a = Automaton.compile(ps);
                    combined = combined == null ? a : combined.intersect(a);
                } catch (AutomatonException e) {
                    throw new JsonGenerationException("allOf pattern 不支持: " + ps, e);
                }
            }
            if (combined != null) {
                if (!combined.isSatisfiable()) {
                    throw new JsonGenerationException("allOf pattern 交集为空: " + patterns);
                }
                return combined.sample(target, ctx.random().random());
            }
        }
        // 目标长度传入 regex：可变长量词朝其靠拢，硬约束由量词自身保证
        return RegexStringGenerator.of(patterns.get(0), ctx.random().random()).generate(target);
    }

    /** 本节点需满足的全部正则（allOf 合并的 _patterns + 自身 pattern）。 */
    private List<String> patterns() {
        List<String> out = new ArrayList<>();
        if (schema.get("_patterns") instanceof List<?> list) {
            for (Object p : list) {
                if (p instanceof String ps && !out.contains(ps)) {
                    out.add(ps);
                }
            }
        }
        if (schema.get("pattern") instanceof String p && !out.contains(p)) {
            out.add(p);
        }
        return out;
    }

    /** 回退生成的目标长度：有上界取区间中点，无上界取 min 与默认长度的较大者。 */
    private static int targetLengthFor(int min, int max) {
        if (max < 0) {
            return Math.max(min, 12);
        }
        if (max < min) {
            return min;
        }
        return (min + max) / 2;
    }

    /** 无正则时的兜底：按长度区间造随机串。 */
    private String randomStringInRange(GenerationContext ctx, int min, int max) {
        if (max < 0) {
            return ctx.random().randomAlnum(Math.max(min, ctx.random().intBetween(4, 12)));
        }
        if (max < min) {
            return ctx.random().randomAlnum(min);
        }
        return ctx.random().randomAlnum(ctx.random().intBetween(min, max));
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
        return inferType(schema);
    }

    /** 无随机地推断类型（类型推断与最小实例共用；type 为列表时取首个）。 */
    private static String inferType(Map<String, Object> schema) {
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

    /**
     * 生成满足节点硬约束的最小实例（递归截断/硬深度兜底用）。
     * 只保证 required/minProperties/minItems/minLength/minimum 等硬约束，
     * 不展开可选部分，也不递归展开 {@code $ref}（最深层以类型最小实例终止）。
     */
    private static Object minimalSatisfying(GenerationNode node, GenerationContext ctx) {
        if (node.alwaysInvalid) {
            throw new JsonGenerationException("false schema 无法生成实例");
        }
        if (node.alwaysValid) {
            return null; // true schema 接受一切，最小实例取 null
        }
        String type = typeOf(node);
        switch (type) {
            case "object" -> {
                return minimalObject(node, ctx);
            }
            case "array" -> {
                return minimalArray(node, ctx);
            }
            case "string" -> {
                int min = intOf(node.schema.get("minLength"), 0);
                return "a".repeat(Math.min(min, 4096));
            }
            case "integer" -> {
                return minimalNumber(node).longValue();
            }
            case "number" -> {
                return minimalNumber(node);
            }
            case "boolean" -> {
                return Boolean.FALSE;
            }
            case "null" -> {
                return null;
            }
            default -> {
                return null;
            }
        }
    }

    /** object 最小实例：只生成 required 字段；minProperties 超出时补空字符串字段。 */
    private static Object minimalObject(GenerationNode node, GenerationContext ctx) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (node.schema.get("required") instanceof List<?> required) {
            for (Object r : required) {
                if (r instanceof String name) {
                    result.put(name, minimalProperty(node, name, ctx));
                }
            }
        }
        int minProps = intOf(node.schema.get("minProperties"), 0);
        int i = 0;
        while (result.size() < minProps) {
            String name = "__p" + i++;
            if (!result.containsKey(name)) {
                result.put(name, "");
            }
        }
        return result;
    }

    /** array 最小实例：满足 minItems。 */
    private static Object minimalArray(GenerationNode node, GenerationContext ctx) {
        int minItems = intOf(node.schema.get("minItems"), 0);
        int minContains = node.schema.get("contains") instanceof Map
                ? intOf(node.schema.get("minContains"), 1) : 0;
        int count = Math.max(minItems, minContains);
        List<Object> result = new ArrayList<>();
        Object itemNode = node.schema.get("items") instanceof Map
                ? node.compiler.compile(node.schema.get("items"), node.baseUri) : null;
        for (int i = 0; i < count; i++) {
            if (itemNode instanceof GenerationNode gn) {
                result.add(minimalSatisfying(gn, ctx));
            } else {
                result.add(ctx.random().randomAlnum(2));
            }
        }
        return result;
    }

    /** 生成 required 字段的最小值：子节点可编译则递归 minimal，否则 null。 */
    private static Object minimalProperty(GenerationNode node, String name, GenerationContext ctx) {
        Object propNode = null;
        if (node.schema.get("properties") instanceof Map<?, ?> props) {
            Object sub = props.get(name);
            if (sub != null) {
                propNode = sub;
            }
        }
        if (propNode == null && node.schema.get("additionalProperties") instanceof Map) {
            propNode = node.schema.get("additionalProperties");
        }
        if (propNode != null) {
            GenerationNode gn = node.compiler.compile(propNode, node.baseUri);
            return minimalSatisfying(gn, ctx);
        }
        return null;
    }

    /** number 最小实例：取下界（exclusive 取+1），multipleOf 对齐。 */
    private static BigDecimal minimalNumber(GenerationNode node) {
        BigDecimal min = bound(node.schema.get("minimum"), node.schema.get("exclusiveMinimum"), true);
        if (min == null) {
            min = BigDecimal.ZERO;
        }
        if (node.schema.get("multipleOf") instanceof Number m && m.doubleValue() > 0) {
            BigDecimal mult = BigDecimal.valueOf(m.doubleValue());
            min = min.divideToIntegralValue(mult).multiply(mult);
        }
        return min;
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
