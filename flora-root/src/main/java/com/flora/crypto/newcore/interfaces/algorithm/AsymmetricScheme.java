package com.flora.crypto.newcore.interfaces.algorithm;

import com.flora.common.algorithm.Algorithm;
import com.flora.common.algorithm.AlgorithmFactory;
import com.flora.crypto.newcore.interfaces.material.param.CipherParameter;

import java.security.SecureRandom;

/**
 * 非对称消息编码方案接口（如 RSAES-OAEP、RSAES-PKCS1-v1_5、RSASSA-PSS）。
 * <p>在密码学标准（RFC 8017）里它们整体称为 scheme，而非 padding：「padding」只是方案内部的
 * 一个组成部分。scheme 的职责是把任意长度的「消息」编码成一个能被底层
 * {@link AsymmetricBlockCipher} 直接处理的「编码消息块」（encode），或反向还原（decode）。</p>
 * <p>它与对称侧的 {@link Padding} 在语义上彻底分离：{@link Padding} 只是长度对齐的字节模式
 * （密钥无关），而本接口是依赖摘要/MGF/随机数的「消息编码器 + 随机化安全构造」，
 * {@code getInputBlockSize} / {@code getOutputBlockSize} 反映的是经方案编码后相对底层引擎块大小的净容量变化。</p>
 * <p>典型实现：OAEPEncoding（配 {@link MaskGenerationFunction} 作 MGF1）、PKCS1Encoding。</p>
 */
public interface AsymmetricScheme extends Algorithm<AlgorithmFactory<? extends AsymmetricScheme>> {

    /**
     * 初始化方案。
     *
     * @param forEncryption 是否为加密/编码方向
     * @param params        底层引擎初始化参数（通常为非对称密钥参数）
     * @param random        随机数源（OAEP/PKCS1v1.5 编码需随机化；可为 {@code null} 当解码方向）
     * @throws IllegalArgumentException 若参数或随机数源不合规
     */
    void init(boolean forEncryption, CipherParameter params, SecureRandom random) throws IllegalArgumentException;

    /**
     * 把底层引擎（编码前）可处理的最大输入长度返回给上层，供分块。
     *
     * @return 编码前允许的最大输入字节数
     */
    int getInputBlockSize();

    /**
     * 返回经本方案编码后、交给底层引擎的单块字节长度。
     *
     * @return 编码消息块字节数（通常等于底层密钥字节长度）
     */
    int getOutputBlockSize();

    /**
     * 将 {@code in[inOff..inOff+inLen]} 编码为一个完整的编码消息块。
     *
     * @return 编码后的字节（长度等于 {@link #getOutputBlockSize()}）
     * @throws IllegalArgumentException 若输入超过 {@link #getInputBlockSize()} 或编码失败
     */
    byte[] encode(byte[] in, int inOff, int inLen) throws IllegalArgumentException;

    /**
     * 将 {@code in[inOff..inOff+len]} 还原为原始消息。
     *
     * @return 解码后的原始消息字节
     * @throws IllegalArgumentException 若编码格式非法或完整性校验失败（如 OAEP 解码一致性检查不过）
     */
    byte[] decode(byte[] in, int inOff, int len) throws IllegalArgumentException;
}
