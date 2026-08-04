package com.flora.crypto.core.bridge;

import com.flora.crypto.core.interfaces.provider.EntropySource;

import com.flora.java.CheckUtil;

import java.security.SecureRandom;

/**
 * 基于 JDK {@link SecureRandom} 的熵源实现。
 * <p>从操作系统随机数发生器取熵，默认抗预测。供 DRBG（如 {@code HMacDrbg}）作种子来源。</p>
 */
public final class SecureRandomEntropySource implements EntropySource {

    private final SecureRandom random;
    private final boolean predictionResistant;
    private final int entropyBits;

    public SecureRandomEntropySource() {
        this(new SecureRandom(), true, 256);
    }

    public SecureRandomEntropySource(SecureRandom random, boolean predictionResistant, int entropyBits) {
        CheckUtil.notNull(random, "随机源不能为空");
        CheckUtil.mustTrue(entropyBits > 0 && (entropyBits % 8 == 0), "熵位数须为正且为 8 的倍数");
        this.random = random;
        this.predictionResistant = predictionResistant;
        this.entropyBits = entropyBits;
    }

    @Override
    public String getAlgorithmName() {
        return "SecureRandom";
    }

    @Override
    public boolean isPredictionResistant() {
        return predictionResistant;
    }

    @Override
    public byte[] getEntropy(int numBits) {
        CheckUtil.mustTrue(numBits > 0, "熵位数须为正");
        byte[] out = new byte[(numBits + 7) / 8];
        random.nextBytes(out);
        return out;
    }

    @Override
    public int entropySize() {
        return entropyBits;
    }
}
