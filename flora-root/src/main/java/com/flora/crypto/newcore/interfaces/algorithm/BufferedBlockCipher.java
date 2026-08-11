package com.flora.crypto.newcore.interfaces.algorithm;

import com.flora.common.algorithm.Algorithm;
import com.flora.common.algorithm.AlgorithmFactory;
import com.flora.crypto.newcore.interfaces.material.param.CipherParameter;

/**
 * 缓冲型分组密码组合器角色接口。
 * <p>与 {@link BlockCipher}（逐块原语）及 {@link LinkedBlockCipher}（链式模式）均不同：
 * 本接口代表「包裹一个 {@link BlockCipher} 原语、缓冲任意长度输入并成块吐出」的组合器，
 * 位于原语层而非模式层。典型实现：{@code BufferedBlockCipher}（仅缓冲，NoPadding）、
 * {@code PaddedBufferedBlockCipher}（缓冲 + 填充）。</p>
 * <p>对外以整段处理的形态暴露：{@link #process(byte[])} 接收任意长度数据，内部缓冲并成块处理，
 * 末尾 {@link #doFinal()} 冲刷剩余不足一块的数据（无填充实现下将报错）。</p>
 */
public interface BufferedBlockCipher extends Algorithm<AlgorithmFactory<? extends BufferedBlockCipher>> {

    /**
     * 初始化组合器并透传到底层原语。
     *
     * @param forEncryption 是否为加密方向
     * @param params        参数（对称密钥；CBC/CFB 等模式包裹时还需 {@link com.flora.crypto.newcore.interfaces.material.param.ParameterWithIV}）
     */
    void init(boolean forEncryption, CipherParameter params);

    /** @return 算法名 / transformation */
    String getAlgorithmName();

    /** @return 底层块大小（字节） */
    int getBlockSize();

    /**
     * 处理整段数据（内部缓冲成块）。
     *
     * @param data 明文或密文（长度任意）
     * @return 处理结果
     */
    byte[] process(byte[] data);

    /**
     * 处理 {@code data[off..off+len]}。
     *
     * @param data 输入缓冲
     * @param off  起始偏移
     * @param len  长度
     * @return 处理结果
     */
    byte[] process(byte[] data, int off, int len);

    /**
     * 收尾：冲刷缓冲中最后的不足一块数据并产出。
     * <p>无填充实现下，若残留不足一块将抛出 {@link IllegalStateException}。</p>
     *
     * @return 最后一段已处理字节
     */
    byte[] doFinal();
}
