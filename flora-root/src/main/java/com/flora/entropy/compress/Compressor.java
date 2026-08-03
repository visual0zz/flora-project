package com.flora.entropy.compress;

import com.flora.crypto.core.interfaces.provider.AlgorithmFamily;

/**
 * 压缩引擎接口。
 * <p>对应常见算法：DEFLATE、GZIP 等。提供流式压缩/解压与一次性便捷入口。
 * 实现类通过 {@link AlgorithmFamily} 自述支持的算法集合与优先级，
 * 由 {@link CompressorProvider} 按算法名注册与分发。</p>
 */
public interface Compressor extends AlgorithmFamily {

    /** @return 算法名，如 {@code "DEFLATE"} */
    String getAlgorithmName();

    // ── 流式压缩 ──

    /**
     * 喂入待压缩数据。
     *
     * @param in    输入缓冲区
     * @param inOff 输入偏移
     * @param len   输入长度
     */
    void update(byte[] in, int inOff, int len);

    /**
     * 完成压缩并将全部压缩数据写入 {@code out}。
     *
     * @param out    输出缓冲区
     * @param outOff 输出偏移
     * @return 写入的压缩字节数
     */
    int doFinal(byte[] out, int outOff);

    /**
     * 重置压缩器状态，以便复用。
     */
    void reset();

    // ── 流式解压 ──

    /**
     * 喂入压缩数据。
     *
     * @param in    输入缓冲区
     * @param inOff 输入偏移
     * @param len   输入长度
     */
    void decompressUpdate(byte[] in, int inOff, int len);

    /**
     * 完成解压并将全部解压数据写入 {@code out}。
     *
     * @param out    输出缓冲区
     * @param outOff 输出偏移
     * @return 写入的解压字节数
     */
    int decompressDoFinal(byte[] out, int outOff);

    /**
     * 重置解压器状态，以便复用。
     */
    void decompressReset();

    // ── 一次性便捷入口 ──

    /**
     * 一次性压缩整段数据。
     *
     * @param data 待压缩数据
     * @return 压缩后的数据
     */
    byte[] compress(byte[] data);

    /**
     * 一次性解压整段数据。
     *
     * @param data 压缩数据
     * @return 解压后的数据
     */
    byte[] decompress(byte[] data);
}
