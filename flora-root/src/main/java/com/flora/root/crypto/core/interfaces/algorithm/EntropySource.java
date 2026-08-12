package com.flora.root.crypto.core.interfaces.algorithm;

import com.flora.root.common.register.Algorithm;
import com.flora.root.common.register.AlgorithmFactory;

/**
 * 熵源（Entropy Source）接口。
 * <p>向确定性随机比特生成器（DRBG）供应不可预测的随机种子。
 * 典型实现从操作系统噪声（{@code SecureRandom}）取熵；也可由硬件 RNG 或测试固定向量支撑。</p>
 */
public interface EntropySource extends Algorithm<AlgorithmFactory<? extends EntropySource>> {

    /** @return 该源是否抗预测（如来自 OS 噪声而非软件伪随机） */
    boolean isPredictionResistant();

    /**
     * 取指定位数的熵。
     *
     * @param numBits 需要的熵位数（实现应取 {@code ceil(numBits/8)} 字节）
     * @return 熵字节
     */
    byte[] getEntropy(int numBits);

    /** @return 该源每次可提供的最大熵位数（应为 8 的整数倍） */
    int entropySize();
}
