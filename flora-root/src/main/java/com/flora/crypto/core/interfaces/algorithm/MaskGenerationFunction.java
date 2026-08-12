package com.flora.crypto.core.interfaces.algorithm;

import com.flora.common.register.Algorithm;
import com.flora.common.register.AlgorithmFactory;

/**
 * 掩码生成函数（MGF）接口，对应 PKCS#1 的 mask generation function。
 * <p>把一段种子确定性扩展为任意长度的伪随机掩码，是 OAEP（加密）与 PSS（签名）方案的核心依赖。
 * 最典型实现为 MGF1（基于某摘要迭代扩展）。本接口刻意与 {@link Digest} / {@link ExtendableOutputFunction}
 * 分离：MGF 的语义是「以种子为输入的、有界长度的、方案专用的掩码生成」，调用方通常是
 * {@link AsymmetricScheme} 而非独立使用。</p>
 */
public interface MaskGenerationFunction extends Algorithm<AlgorithmFactory<? extends MaskGenerationFunction>> {

    /**
     * 将 {@code seed} 扩展为 {@code length} 字节掩码写入 {@code out}。
     *
     * @param seed   输入种子
     * @param seedOff 种子偏移
     * @param seedLen 种子长度
     * @param out     输出缓冲
     * @param outOff  输出偏移
     * @param length  需要的掩码字节数
     * @throws IllegalArgumentException 若 {@code length} 超过本函数可生成的上限
     */
    void generateMask(byte[] seed, int seedOff, int seedLen, byte[] out, int outOff, int length)
            throws IllegalArgumentException;
}
