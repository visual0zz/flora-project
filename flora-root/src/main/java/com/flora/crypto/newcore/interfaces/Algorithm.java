package com.flora.crypto.newcore.interfaces;

import java.util.Set;

/**
 * 算法族自述接口。
 * <p>所有算法族接口（{@code Digest} / {@code Mac} / {@code BlockCipher} 等）继承本接口，
 * 使每个实现类直接通过族接口描述自己支持的算法集合与分发优先级。
 * CryptoProvider 按 {@code supportedAlgorithms()} 注册，分发时按「能实现 → 优先级 → 具体度」裁决。</p>
 */
public interface Algorithm<T extends AlgorithmFactory<?>> extends AlgorithmComponent{
    T factory();
    String getAlgorithmName();
}
