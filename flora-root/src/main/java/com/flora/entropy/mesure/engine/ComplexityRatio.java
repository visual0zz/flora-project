package com.flora.entropy.mesure.engine;

import com.flora.entropy.mesure.EntropyMetric;

import java.util.Set;
import java.util.zip.Deflater;

/**
 * 压缩复杂度比度量（Kolmogorov 复杂度的工程近似）。
 * <p>以 Deflate 最高压缩级别压缩输入字节，把「不可压缩度」映射为每字节熵近似：
 * {@code min(压缩后长度 / 原长度, 1) × 8}，范围 {@code [0,8]}——随机数据接近 8，
 * 高度重复或结构化文本明显偏低。算法只输出熵总量，上限与归一化由汇总层按字节长度推导。</p>
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
    public double measure(byte[] data) {
        if (data == null || data.length == 0) {
            return 0.0;
        }
        // 压缩比可能因头部开销略超 1，先截断再映射为每字节熵（bit/字节），供密度归一化层使用
        return Math.min(compressRatio(data), 1.0) * 8.0;
    }

    /**
     * 压缩复杂度比（压缩后长度 / 原长，范围 {@code [0,1]}）。
     * <p>与 {@link #measure(byte[])}（每字节熵近似，含 {@code ×8} 缩放以契合密度归一化框架）不同，
     * 本方法直接返回未缩放的"可压缩度"语义比值，供门面按原始压缩比对外暴露，
     * 调用方无需逆向 {@code ×8} 的内部约定。</p>
     *
     * @param data 待评估字节数组，{@code null} 或空数组返回 0
     * @return 压缩后长度 / 原长，截断在 {@code [0,1]}
     */
    public static double ratio(byte[] data) {
        if (data == null || data.length == 0) {
            return 0.0;
        }
        return Math.min(compressRatio(data), 1.0);
    }

    /** Deflate 最高压缩级别压缩后长度与原长的比值。 */
    private static double compressRatio(byte[] data) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(data);
        deflater.finish();
        byte[] buf = new byte[data.length + 64];
        int compressed = deflater.deflate(buf);
        deflater.end();
        return (double) compressed / data.length;
    }
}
