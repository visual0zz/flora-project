package com.flora.crypto.newcore.interfaces;

import java.util.Set;

/**
 * 算法族自述接口。
 * <p>所有算法族接口（{@code Digest} / {@code Mac} / {@code BlockCipher} 等）继承本接口。
 * 同时本接口 {@code extends AlgorithmComponent}，即「算法本身也是一种组件」——
 * 组合算法构造时注入的其它算法直接以 {@code Algorithm} 实例承担。</p>
 * <p>每个实现类通过 {@link #factory()} 自述其工厂，通过 {@link #getAlgorithmName()} 自述算法名；
 * CryptoProvider 按 {@code AlgorithmFactory#supportedAlgorithms()} 注册，
 * 分发时按「能实现 → 优先级 → 具体度」裁决。</p>
 */
public interface Algorithm<T extends AlgorithmFactory<?>> extends AlgorithmComponent{
    T factory();
    String getAlgorithmName();
}
