package com.flora.entropy.mesure;

import com.flora.crypto.core.interfaces.provider.AlgorithmFamily;

/**
 * 熵度量算法接口：把字节数据映射为一个<b>熵总量</b>标量。
 * <p>输入统一为 {@code byte[]}，输出为未归一化的原始度量（如每字节香农熵、压缩不可压缩度），
 * 算法自身<b>不计算</b>上限或归一化密度——这些统一交给汇总层
 * （{@link EntropyProvider}，按输入字节长度推导熵上限）换算，避免各算法重复实现、逻辑漂移。</p>
 * <p>新算法只需实现 {@link #measure(byte[])} 并注册到 {@link EntropyProvider}，
 * 即可自动参与 {@link EntropyProvider#minDensity} 聚合。</p>
 */
public interface EntropyMetric extends AlgorithmFamily {

    /** @return 算法名，如 {@code "SHANNON"} */
    String getAlgorithmName();

    /**
     * 度量字节数据的熵总量（未归一化，语义由各实现自述，如 bit/字节）。
     *
     * @param data 待评估字节数组，{@code null} 或空数组返回 0
     * @return 熵总量
     */
    double measure(byte[] data);
}
