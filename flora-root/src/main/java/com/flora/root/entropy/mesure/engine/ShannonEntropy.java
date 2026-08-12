package com.flora.root.entropy.mesure.engine;

import com.flora.root.common.register.AlgorithmComponent;
import com.flora.root.common.register.AlgorithmFactory;
import com.flora.root.common.register.AlgorithmFactoryRegister;
import com.flora.root.entropy.mesure.EntropyMetric;
import com.flora.root.entropy.mesure.EntropyMetricAlgorithmFactoryRegister;

import java.util.Set;

/**
 * 香农熵度量（Shannon entropy）。
 * <p>衡量字节分布的不确定度，返回每字节香农熵（bit/字节，范围 {@code [0,8]}）；
 * 越接近均匀分布，熵越高。算法只输出熵总量，上限与归一化由汇总层按字节长度推导。</p>
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
    public double measure(byte[] data) {
        if (data == null || data.length == 0) {
            return 0.0;
        }
        int[] freq = new int[256];
        for (byte b : data) {
            freq[b & 0xFF]++;
        }
        double h = 0.0;
        for (int c : freq) {
            if (c == 0) {
                continue;
            }
            double p = (double) c / data.length;
            h -= p * Math.log(p);
        }
        return h / Math.log(2);
    }

    @Override
    public AlgorithmFactory<? extends EntropyMetric> factory() {
        return FACTORY;
    }

    public static final AlgorithmFactory<EntropyMetric> FACTORY = new AlgorithmFactory<>() {
        @Override
        public Class<? extends AlgorithmFactoryRegister> registerTo() {
            return EntropyMetricAlgorithmFactoryRegister.class;
        }

        @Override
        public Set<String> supportedAlgorithms() {
            return SUPPORTED;
        }

        @Override
        public int priority() {
            return 0;
        }

        @Override
        public Class<AlgorithmComponent>[] componentTypes() {
            return new Class[0];
        }

        @Override
        public EntropyMetric construct(String algorithmName, AlgorithmComponent... components) {
            return new ShannonEntropy();
        }
    };
}
