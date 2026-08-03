package com.flora.entropy.mesure.engine;

import com.flora.entropy.mesure.EntropyMetric;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 香农熵度量（Shannon entropy）。
 * <p>衡量字符/字节分布的不确定度，单位为 bit/符号；越接近均匀分布，熵越高。
 * 同时支持字符串（按码点计）和字节数组输入。</p>
 */
public final class ShannonEntropy implements EntropyMetric {

    private static final String NAME = "SHANNON";
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
        return shannonFromCounts(counts.values(), total);
    }

    @Override
    public double measure(byte[] data) {
        if (data == null || data.length == 0) {
            return 0.0;
        }
        int[] freq = new int[256];
        for (byte b : data) {
            freq[b & 0xFF]++;
        }
        int total = data.length;
        double h = 0.0;
        for (int c : freq) {
            if (c == 0) {
                continue;
            }
            double p = (double) c / total;
            h -= p * Math.log(p);
        }
        return h / Math.log(2);
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
