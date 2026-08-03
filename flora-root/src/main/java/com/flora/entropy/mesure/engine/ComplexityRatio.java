package com.flora.entropy.mesure.engine;

import com.flora.entropy.mesure.EntropyMetric;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.zip.Deflater;

/**
 * 压缩复杂度比度量（Kolmogorov 复杂度的工程近似）。
 * <p>以 Deflate 最高压缩级别压缩 UTF-8 字节，返回压缩后长度与原长的比值。
 * 接近 {@code 1} 表示不可压缩（高随机）；接近 {@code 0} 表示高度重复或结构化文本。
 * 注意：对很短的串，压缩头开销会使比值偏高，解释结果时应结合长度。</p>
 */
public final class ComplexityRatio implements EntropyMetric {

    private static final String NAME = "COMPLEXITY_RATIO";
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
        byte[] data = s.getBytes(StandardCharsets.UTF_8);
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(data);
        deflater.finish();
        byte[] buf = new byte[data.length + 64];
        int compressed = deflater.deflate(buf);
        deflater.end();
        return (double) compressed / data.length;
    }
}
