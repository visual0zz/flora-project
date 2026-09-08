package com.flora.root.mock.jsonschema.impl;

import com.flora.root.mock.jsonschema.JsonGenerationException;
import com.flora.root.mock.regex.automaton.Automaton;
import com.flora.root.mock.regex.automaton.AutomatonException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 单节点生成规则。持有原始 schema（已合并 allOf）与本节点须满足的全部正则，
 * 运行时按关键字优先级分派到各类型生成器。
 * <p>正则在编译期收集进 {@link #patterns}（allOf 各分支 + 自身），
 * 交集自动机惰性构造并缓存，同一节点反复生成不会重复编译。</p>
 */
public final class GenerationNode {

    /** 纯防溢出保险：递归深度绝对上限（正常由随深度递减的展开概率收敛，几乎不可能触发）。 */
    static final int HARD_DEPTH_LIMIT = 1000;

    final boolean alwaysInvalid;
    final boolean alwaysValid;
    final Map<String, Object> schema;
    final List<String> patterns;
    final String baseUri;
    final GeneratorCompiler compiler;

    private Automaton automaton;

    GenerationNode(boolean value, GeneratorCompiler compiler) {
        this.alwaysValid = value;
        this.alwaysInvalid = !value;
        this.schema = null;
        this.patterns = List.of();
        this.baseUri = "";
        this.compiler = compiler;
    }

    GenerationNode(Map<String, Object> schema, List<String> patterns, String baseUri,
                   GeneratorCompiler compiler) {
        this.alwaysValid = false;
        this.alwaysInvalid = false;
        this.schema = schema;
        this.patterns = patterns == null ? List.of() : List.copyOf(patterns);
        this.baseUri = baseUri;
        this.compiler = compiler;
    }

    public Object generate(GenerationContext ctx) {
        if (alwaysInvalid) {
            throw new JsonGenerationException("false schema 无法生成实例");
        }
        if (alwaysValid) {
            return Nodes.randomScalar(ctx.random());
        }
        if (schema.containsKey("const")) {
            return schema.get("const");
        }
        if (schema.get("enum") instanceof List<?> enumValues && !enumValues.isEmpty()) {
            return enumValues.get(ctx.random().random().nextInt(enumValues.size()));
        }
        if (schema.containsKey("$ref") || schema.containsKey("$dynamicRef")) {
            String ref = Nodes.str(schema.containsKey("$ref")
                    ? schema.get("$ref") : schema.get("$dynamicRef"));
            GenerationNode target = compiler.resolveRef(ref, baseUri);
            if (ctx.onPath(target.schema)) {
                // 循环引用：随深度递减的概率决定是否继续展开（越深越可能截断）
                return ctx.shouldExpand() ? expandRecursive(target, ctx)
                        : MinimalInstance.of(target, ctx);
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
            return generateConditional(ctx);
        }
        if (ctx.depth() >= HARD_DEPTH_LIMIT) {
            return MinimalInstance.of(this, ctx);
        }
        return generateByType(pickType(ctx), ctx);
    }

    // ── 递归与分支 ──

    /** 展开 $ref 目标节点：加入路径防循环检测，生成后移除（异常时也保证退出）。 */
    private Object expandRecursive(GenerationNode target, GenerationContext ctx) {
        ctx.enterPath(target.schema);
        try {
            return target.generate(ctx.deeper());
        } finally {
            ctx.exitPath(target.schema);
        }
    }

    /** if/then/else：随机走一个存在的分支。 */
    private Object generateConditional(GenerationContext ctx) {
        boolean takeThen = ctx.random().nextBoolean();
        Object branch = takeThen ? schema.get("then") : schema.get("else");
        if (branch == null) {
            branch = takeThen ? schema.get("else") : schema.get("then");
        }
        if (branch == null) {
            return generateByType(pickType(ctx), ctx);
        }
        return compiler.compile(branch, baseUri).generate(ctx.deeper());
    }

    private Object generateByType(String type, GenerationContext ctx) {
        return switch (type) {
            case "object" -> ObjectGenerator.generate(this, ctx);
            case "array" -> ArrayGenerator.generate(this, ctx);
            case "string" -> StringGenerator.generate(this, ctx);
            case "integer" -> NumberGenerator.generate(this, ctx, true);
            case "number" -> NumberGenerator.generate(this, ctx, false);
            case "boolean" -> ctx.random().nextBoolean();
            case "null" -> null;
            default -> Nodes.randomScalar(ctx.random());
        };
    }

    // ── 正则 ──

    /** 本节点须满足的全部正则（allOf 各分支 + 自身）。 */
    List<String> patterns() {
        return patterns;
    }

    /**
     * 本节点全部正则的交集自动机（惰性构造并缓存）；无正则返回 null。
     *
     * @throws AutomatonException 正则不受支持或交集为空，由调用方决定降级
     */
    Automaton automaton() {
        if (patterns.isEmpty()) {
            return null;
        }
        if (automaton == null) {
            Automaton combined = null;
            for (String pattern : patterns) {
                Automaton compiled = Automaton.compile(pattern);
                combined = combined == null ? compiled : combined.intersect(compiled);
            }
            if (!combined.isSatisfiable()) {
                throw new AutomatonException("正则交集为空: " + patterns);
            }
            automaton = combined;
        }
        return automaton;
    }

    // ── 属性与分支 ──

    /** 取属性对应的子节点：properties 优先，其次命中名字的 patternProperties，最后 additionalProperties。 */
    GenerationNode propertyNode(String name) {
        if (schema.get("properties") instanceof Map<?, ?> props) {
            Object node = props.get(name);
            if (node != null) {
                return compiler.compile(node, baseUri);
            }
        }
        if (schema.get("patternProperties") instanceof Map<?, ?> patterns) {
            for (Map.Entry<?, ?> e : patterns.entrySet()) {
                if (name.matches(Nodes.str(e.getKey()))) {
                    return compiler.compile(e.getValue(), baseUri);
                }
            }
        }
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
        return Nodes.inferType(schema, patterns);
    }
}
