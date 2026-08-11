package com.flora.crypto.newcore.interfaces.algorithm;

/**
 * 可变长输出函数（Extendable-Output Function，XOF）接口。
 * <p>与定长 {@link Digest} 不同，XOF 可以按调用方需要吐出任意长度的字节，
 * 代表算法：SHAKE128 / SHAKE256、SHA-3 XOF、cSHAKE、KangarooTwelve 等。</p>
 */
public interface ExtendableOutputFunction extends Digest {

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
