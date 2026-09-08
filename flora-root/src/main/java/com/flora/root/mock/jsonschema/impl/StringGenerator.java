package com.flora.root.mock.jsonschema.impl;

import com.flora.root.mock.regex.automaton.Automaton;
import com.flora.root.mock.regex.automaton.AutomatonException;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * string 节点生成：语义优先，拒绝回退。
 * <ol>
 *   <li>按 {@code format}（优先）或字段名语义造候选值；</li>
 *   <li>校验长度区间与全部 {@code pattern}，通过即采用；</li>
 *   <li>连续 {@link SemanticStringGenerator#MAX_REJECTIONS} 次被拒后放弃语义，
 *       改用本节点正则的交集自动机直接采样；</li>
 *   <li>正则不受支持（或没有正则）时降级为长度区间内的随机串——单个字段的正则
 *       不认识不该让整份数据生成失败。</li>
 * </ol>
 */
final class StringGenerator {

    /** 无上界时的默认目标长度。 */
    private static final int DEFAULT_TARGET = 12;
    /** 无上界时随机串的长度下界。 */
    private static final int RANDOM_MIN = 4;
    /** 无上界时随机串的长度上界。 */
    private static final int RANDOM_MAX = 12;

    private StringGenerator() {
    }

    static Object generate(GenerationNode node, GenerationContext ctx) {
        int min = Nodes.intOf(node.schema.get("minLength"), 0);
        int max = Nodes.intOf(node.schema.get("maxLength"), -1); // -1 表示无上界
        SemanticStringGenerator semantic = new SemanticStringGenerator(ctx.random());
        for (int attempt = 0; attempt < SemanticStringGenerator.MAX_REJECTIONS; attempt++) {
            String candidate = candidate(node, semantic, ctx);
            if (fits(candidate, node, min, max)) {
                return candidate;
            }
        }
        return fallback(node, ctx, min, max);
    }

    /** 语义候选值：{@code format} 优先，其次按属性名猜测字段含义生成。 */
    private static String candidate(GenerationNode node, SemanticStringGenerator semantic,
                                    GenerationContext ctx) {
        if (node.schema.get("format") instanceof String format) {
            return new FormatGenerator(ctx.random()).generate(format);
        }
        return semantic.generate(ctx.name());
    }

    /** 候选值是否合规：长度落在区间内，且命中全部 pattern。 */
    private static boolean fits(String value, GenerationNode node, int min, int max) {
        if (value == null) {
            return false;
        }
        if (value.length() < min || (max >= 0 && value.length() > max)) {
            return false;
        }
        for (String pattern : node.patterns()) {
            if (!matches(pattern, value)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 正则命中判定：与校验侧一致走 JDK {@code java.util.regex} 的 {@code find()}。
     * 校验侧无法编译的正则视为不合规（交由回退路径处理）。
     */
    private static boolean matches(String pattern, String value) {
        try {
            return Pattern.compile(pattern).matcher(value).find();
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    private static String fallback(GenerationNode node, GenerationContext ctx, int min, int max) {
        try {
            Automaton automaton = node.automaton();
            if (automaton != null) {
                return automaton.sample(targetLength(min, max, automaton.minLength()),
                        ctx.random().random());
            }
        } catch (AutomatonException e) {
            // 正则不受支持：降级为按长度区间的随机串
        }
        return randomInRange(ctx, min, max);
    }

    /** 回退生成的目标长度：取区间中值，并提升到语言最小长度。 */
    private static int targetLength(int min, int max, int automatonMin) {
        int target;
        if (max < 0) {
            target = Math.max(min, DEFAULT_TARGET);
        } else if (max < min) {
            target = min;
        } else {
            target = (min + max) / 2;
        }
        return Math.max(target, automatonMin);
    }

    private static String randomInRange(GenerationContext ctx, int min, int max) {
        if (max < 0) {
            return ctx.random().randomAlnum(Math.max(min, ctx.random().intBetween(RANDOM_MIN, RANDOM_MAX)));
        }
        if (max < min) {
            return ctx.random().randomAlnum(min);
        }
        return ctx.random().randomAlnum(ctx.random().intBetween(min, max));
    }
}
