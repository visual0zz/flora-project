package com.flora.entropy.mesure;

import com.flora.common.register.Algorithm;
import com.flora.common.register.AlgorithmFactory;

import java.util.Set;

/**
 * 熵度量算法接口：把字节数据映射为一个<b>熵总量</b>标量。
 * <p>输入统一为 {@code byte[]}，输出为未归一化的原始度量（如每字节香农熵、压缩不可压缩度），
 * 算法自身<b>不计算</b>上限或归一化密度——这些统一交给汇总层
 * （{@link EntropyEstimator}，按输入字节长度推导熵上限）换算，避免各算法重复实现、逻辑漂移。</p>
 * <p>新算法只需实现 {@link #measure(byte[])} 并注册到 {@link EntropyEstimator}，
 * 即可自动参与 {@link EntropyEstimator#minDensity} 聚合。</p>
 */
public interface EntropyMetric extends Algorithm<AlgorithmFactory<? extends EntropyMetric>> {

    /** @return 支持的算法名集合，默认仅包含本实例的算法名 */
    default Set<String> supportedAlgorithms() {
        return Set.of(getAlgorithmName());
    }

    /** @return 分发优先级，数字越大越优先；通用适配器保持默认 {@code 0} */
    default int priority() {
        return 0;
    }

    /**
     * 度量字节数据的熵总量（未归一化，语义由各实现自述，如 bit/字节）。
     *
     * @param data 待评估字节数组，{@code null} 或空数组返回 0
     * @return 熵总量
     */
    double measure(byte[] data);
}
