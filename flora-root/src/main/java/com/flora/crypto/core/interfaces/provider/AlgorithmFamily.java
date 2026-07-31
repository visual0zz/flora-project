package com.flora.crypto.core.interfaces.provider;

import java.util.Set;

/**
 * 算法族自述接口。
 * <p>所有算法族接口（{@code Digest} / {@code Mac} / {@code BlockCipher} 等）继承本接口，
 * 使每个实现类直接通过族接口描述自己支持的算法集合与分发优先级。
 * CryptoProvider 按 {@code supportedAlgorithms()} 注册，分发时按「能实现 → 优先级 → 具体度」裁决。</p>
 */
public interface AlgorithmFamily {

    /** @return 算法名 */
    String getAlgorithmName();

    /**
     * 支持的算法名集合。默认仅包含本实例的算法名；
     * 多算法实现类（如 JDK 适配器）应覆写返回类级支持的完整集合。
     */
    default Set<String> supportedAlgorithms() {
        return Set.of(getAlgorithmName());
    }

    /**
     * 分发优先级，数字越大越优先。通用适配器应保持默认 {@code 0}，
     * 专用实现可返回更大值以覆盖通用适配器。
     */
    default int priority() {
        return 0;
    }
}
