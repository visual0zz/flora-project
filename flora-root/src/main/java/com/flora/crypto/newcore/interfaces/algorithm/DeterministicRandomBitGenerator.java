package com.flora.crypto.newcore.interfaces.algorithm;

import com.flora.common.algorithm.Algorithm;
import com.flora.common.algorithm.AlgorithmFamily;

/**
 * 确定性随机比特生成器（Deterministic Random Bit Generator，DRBG）接口。
 * <p>给定熵源后，可确定性地生成任意长度随机比特，并支持周期性重播种（reseed）。
 * 概念对应 NIST SP800-90A 定义的 DRBG 框架（HMAC_DRBG / Hash_DRBG / CTR_DRBG），
 * 但接口语义为通用 DRBG 抽象，不限定具体算法。</p>
 */
public interface DeterministicRandomBitGenerator
        extends Algorithm<AlgorithmFamily<? extends DeterministicRandomBitGenerator>> {

    /**
     * 生成随机比特到 {@code output}。
     *
     * @param output 输出缓冲区（长度即请求字节数）
     * @return 生成的比特数（即 {@code output.length * 8}）；返回 {@code -1} 表示需要先 {@link #reseed}
     */
    int generate(byte[] output);

    /** @return 单次生成块字节数（通常为底层原语输出长度） */
    int getBlockSize();

    /**
     * 重播种：拉取新熵并混入工作状态。
     *
     * @param additionalInput 附加输入（可为 {@code null}），参与混入工作状态
     */
    void reseed(byte[] additionalInput);

}
