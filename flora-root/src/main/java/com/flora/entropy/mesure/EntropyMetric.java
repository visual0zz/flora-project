package com.flora.entropy.mesure;

import com.flora.crypto.core.interfaces.provider.AlgorithmFamily;

/**
 * 熵度量算法接口。
 * <p>每种实现自述算法名（如 {@code "SHANNON"}、{@code "NORMALIZED"} 等），
 * 由 {@link EntropyProvider} 按算法名注册与分发。度量值统一为 {@code double}，
 * 离散计数类（如字符类别数）也以 {@code double} 返回。</p>
 */
public interface EntropyMetric extends AlgorithmFamily {

    /** @return 算法名，如 {@code "SHANNON"} */
    String getAlgorithmName();

    /**
     * 度量字符串的熵/随机性。
     *
     * @param s 待评估字符串，{@code null} 或空串返回 0
     * @return 度量值
     */
    double measure(String s);

    /**
     * 度量字节数组的熵/随机性。
     * <p>默认实现抛出 {@link UnsupportedOperationException}，仅部分算法支持字节输入。</p>
     *
     * @param data 待评估字节数组，{@code null} 或空数组返回 0
     * @return 度量值
     * @throws UnsupportedOperationException 若该算法不支持字节输入
     */
    default double measure(byte[] data) {
        throw new UnsupportedOperationException(
                getAlgorithmName() + " does not support byte[] input");
    }
}
