package com.flora.entropy.mesure.engine;

import com.flora.entropy.mesure.EntropyMetric;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 归一化香农熵度量。
 * <p>等于香农熵除以 {@code log2(实际出现的不同码点数)}，落在 {@code [0,1]}，
 * 使不同长度的串可直接比较"相对于自身字母表有多随机"。</p>
 */
public final class NormalizedEntropy implements EntropyMetric {

    private static final String NAME = "NORMALIZED";
    private static final Set<String> SUPPORTED = Set.of(NAME);

    @Override
    public String getAlgorithmName() {
        return NAME;
    }

    @Override
    public Set<String> supportedAlgorithms() {
        return SUPPORTED;
    }

    @Override
    public double measure(String s) {
        if (s == null || s.isEmpty()) {
            return 0.0;
        }
        Map<Integer, Integer> counts = new HashMap<>();
        int total = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            counts.merge(cp, 1, Integer::sum);
            total++;
            i += Character.charCount(cp);
        }
        int alphabet = counts.size();
        if (alphabet <= 1) {
            return 0.0;
        }
        double h = shannonFromCounts(counts.values(), total);
        double max = Math.log(alphabet) / Math.log(2);
        return h / max;
    }

    /** 由频次表计算香农熵（bit/符号）。 */
    private static double shannonFromCounts(Iterable<Integer> counts, int total) {
        double h = 0.0;
        for (int c : counts) {
            double p = (double) c / total;
            h -= p * Math.log(p);
        }
        return h / Math.log(2);
    }
}
