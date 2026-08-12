package com.flora.crypto.core.interfaces.algorithm;

import com.flora.common.algorithm.Algorithm;
import com.flora.common.algorithm.AlgorithmFactory;
import com.flora.crypto.core.interfaces.material.param.CipherParameter;

/**
 * 链式 / 反馈分组密码模式接口（如 CBC / CFB / OFB / CTR）。
 * <p>本接口与 {@link BlockCipher} <b>无任何关系</b>：前者描述「对一串块施加链式 / 反馈规则」的
 * 模式语义（流式处理），后者描述「处理单个块」的密码原语语义；二者抽象层级不同，不可混为一谈。</p>
 * <p>模式自身不实现密码原语，仅通过 {@link AlgorithmFactory#componentTypes()} 声明依赖的底层
 * 块密码（须为无模式的原语，而非另一个模式），构造时注入后组合工作。模式以流式形态暴露：
 * 多次 {@link #update(byte[])} 喂入数据，内部自行完成块链接并即时吐出已对齐的整块结果，
 * 末尾 {@link #doFinal()} 冲刷缓冲中剩余的不足一块的数据（无填充模式下不足一块将报错）。</p>
 * <p>典型实现：{@code CBCBlockCipher} / {@code CFBBlockCipher} / {@code OFBBlockCipher} / {@code SICBlockCipher}。</p>
 */
public interface LinkedBlockCipher extends Algorithm<AlgorithmFactory<? extends LinkedBlockCipher>> {

    /**
     * 初始化模式。
     *
     * @param forEncryption 是否为加密方向
     * @param params        参数（对称密钥；CBC/CFB/OFB/CTR 等还需 {@link com.flora.crypto.core.interfaces.material.param.ParameterWithIV}）
     */
    void init(boolean forEncryption, CipherParameter params);

    /** @return 算法名 / transformation，如 {@code "CBC"} */
    String getAlgorithmName();

    /** @return 底层块大小（字节） */
    int getBlockSize();

    /**
     * 喂入数据并即时产出已完成整块的部分结果。
     *
     * @param data 明文或密文（长度任意，内部缓存不足一块的部分）
     * @return 本次可立即输出的已处理字节（不足一块的尾巴会被缓存，不在此返回）
     */
    byte[] update(byte[] data);

    /**
     * 喂入 {@code data[off..off+len]} 并即时产出已完成整块的部分结果。
     *
     * @param data 输入缓冲
     * @param off  起始偏移
     * @param len  长度
     * @return 本次可立即输出的已处理字节
     */
    byte[] update(byte[] data, int off, int len);

    /**
     * 收尾：冲刷缓冲中最后的不足一块数据并产出。调用后模式内部状态复位，可重新 {@link #init} 复用。
     * <p>无填充模式下，若末尾残留不足一块的数据将抛出 {@link IllegalStateException}；
     * 需要填充请改用 {@code PaddedBufferedBlockCipher} 或在适配器里处理。</p>
     *
     * @return 最后一段已处理字节
     */
    byte[] doFinal();
}
