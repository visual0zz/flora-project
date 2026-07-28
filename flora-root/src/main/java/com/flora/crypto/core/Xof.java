package com.flora.crypto.core;

/**
 * 可变长输出函数（Extendable-Output Function，XOF）接口（Bouncy Castle 风格）。
 * <p>与定长 {@link Digest} 不同，XOF 可以按调用方需要吐出任意长度的字节，
 * 代表算法：SHAKE128 / SHAKE256、SHA-3 XOF、cSHAKE、KangarooTwelve 等。</p>
 * <p>JDK 的 {@code MessageDigest.doFinal()} 是定长语义，<b>没有</b>「给我 N 字节」的概念槽位，
 * 因此本接口对应 JDK 缺失的能力：默认提供最简占位实现 {@code PlaceholderXof}，
 * 待接入 SHAKE 等真实引擎后以 {@code CryptoProvider.registerXof} 覆盖。</p>
 */
public interface Xof extends Digest {

    /**
     * 完成计算并写入 {@code outLen} 字节（可变长输出）。
     *
     * @param out     输出缓冲区
     * @param outOff  起始偏移
     * @param outLen  期望写入的字节数
     * @return 实际写入的字节数（即 {@code outLen}）
     */
    int doFinal(byte[] out, int outOff, int outLen);

    /**
     * 增量吐出可变长输出，可多次调用以持续获取更多字节。
     *
     * @param out     输出缓冲区
     * @param outOff  起始偏移
     * @param outLen  期望写入的字节数
     * @return 实际写入的字节数
     */
    int doOutput(byte[] out, int outOff, int outLen);
}
