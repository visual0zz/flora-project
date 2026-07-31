package com.flora.crypto.core.interfaces.provider;

/**
 * NIST SP800-90A 确定性随机比特生成器（DRBG）接口（Bouncy Castle 风格）。
 * <p>给定熵源后，可确定性地生成任意长度随机比特，并支持周期性重播种（reseed）。
 * 常见实现：HMAC_DRBG / Hash_DRBG / CTR_DRBG。JDK 无第一等 DRBG 抽象（仅 {@code SecureRandom}），
 * 本项目用 {@code HMacDrbg} 提供纯 Java 实现。</p>
 */
public interface SP80090DRBG extends AlgorithmFamily {

    /**
     * 生成随机比特到 {@code output}。
     *
     * @param output             输出缓冲区（长度即请求字节数）
     * @param additionalInput    附加输入（可为 {@code null}），参与本次生成
     * @param predictionResistant 是否要求本次生成抗预测（触发内部重播种）
     * @return 生成的比特数（即 {@code output.length * 8}）；返回 {@code -1} 表示需要先 {@link #reseed}
     */
    int generate(byte[] output, byte[] additionalInput, boolean predictionResistant);

    /** @return 单次生成块字节数（通常为底层原语输出长度） */
    int getBlockSize();

    /**
     * 重播种：拉取新熵并混入工作状态。
     *
     * @param additionalInput 附加输入（可为 {@code null}）
     */
    void reseed(byte[] additionalInput);
}
