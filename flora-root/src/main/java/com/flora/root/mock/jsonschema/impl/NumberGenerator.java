package com.flora.root.mock.jsonschema.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * number / integer 节点生成：在 {@code minimum..maximum} 区间内取值，
 * 有 {@code multipleOf} 时按倍数对齐。
 * <p>{@code multipleOf} 全程用 {@link BigDecimal} 精确运算（不经过 double），
 * 取值方式为"在合法倍数中随机挑一个"，而非先随机再取整截断（后者会落到区间外）。
 * 无 {@code multipleOf} 的 number 会限制小数位数，避免产出 20 位小数的怪值。</p>
 */
final class NumberGenerator {

    /** 无上界时的默认取值跨度。 */
    private static final int DEFAULT_SPAN = 1000;
    /** 无 multipleOf 时的最大小数位数。 */
    private static final int MAX_SCALE = 2;

    private NumberGenerator() {
    }

    static Object generate(GenerationNode node, GenerationContext ctx, boolean integer) {
        BigDecimal min = Nodes.bound(node.schema.get("minimum"), node.schema.get("exclusiveMinimum"), true);
        BigDecimal max = Nodes.bound(node.schema.get("maximum"), node.schema.get("exclusiveMaximum"), false);
        if (min == null) {
            min = BigDecimal.ZERO;
        }
        if (max == null) {
            max = min.add(BigDecimal.valueOf(DEFAULT_SPAN));
        }
        if (min.compareTo(max) > 0) {
            max = min;
        }
        BigDecimal multiple = Nodes.decimalOf(node.schema.get("multipleOf"), null);
        if (multiple != null && multiple.signum() > 0) {
            BigDecimal value = aligned(ctx, min, max, multiple);
            return integer ? value.longValue() : value;
        }
        if (integer) {
            return ctx.random().longBetween(roundUp(min), roundDown(max));
        }
        int scale = ctx.random().intBetween(0, MAX_SCALE);
        return normalize(scaled(ctx.random().decimalBetween(min, max), scale, min, max));
    }

    /** 在 [min, max] 内的合法倍数中随机取一个；区间内无合法倍数时取不小于 min 的最近倍数。 */
    private static BigDecimal aligned(GenerationContext ctx, BigDecimal min, BigDecimal max,
                                      BigDecimal multiple) {
        BigDecimal first = min.divide(multiple, 0, RoundingMode.CEILING).multiply(multiple);
        if (first.compareTo(max) > 0) {
            return first;
        }
        BigDecimal span = max.subtract(first).divideToIntegralValue(multiple);
        if (span.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0) {
            return first;
        }
        long steps = span.longValue();
        return first.add(multiple.multiply(BigDecimal.valueOf(ctx.random().longBetween(0, steps))));
    }

    private static BigDecimal scaled(BigDecimal value, int scale, BigDecimal min, BigDecimal max) {
        BigDecimal scaled = value.setScale(scale, RoundingMode.HALF_UP);
        if (scaled.compareTo(min) < 0) {
            return min.setScale(scale, RoundingMode.CEILING);
        }
        if (scaled.compareTo(max) > 0) {
            return max.setScale(scale, RoundingMode.FLOOR);
        }
        return scaled;
    }

    /** 去掉多余的尾随零；整数值不保留科学计数法形式的负 scale。 */
    private static BigDecimal normalize(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    private static long roundUp(BigDecimal v) {
        return v.setScale(0, RoundingMode.CEILING).longValue();
    }

    private static long roundDown(BigDecimal v) {
        return v.setScale(0, RoundingMode.FLOOR).longValue();
    }
}
