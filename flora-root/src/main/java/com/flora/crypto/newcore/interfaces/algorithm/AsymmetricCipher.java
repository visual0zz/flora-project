package com.flora.crypto.newcore.interfaces.algorithm;

import com.flora.common.algorithm.Algorithm;
import com.flora.common.algorithm.AlgorithmFamily;
import com.flora.crypto.newcore.interfaces.material.param.CipherParameters;

/**
 * 流式非对称密码接口。
 * <p>与 {@link AsymmetricBlockCipher}（整块处理，如 RSA）不同，本接口支持逐字节/逐段流式处理，
 * 用于 ECIES 等把非对称原语当流用、需缓冲分块的场景。底层由缓冲包装器包裹一个
 * {@link AsymmetricBlockCipher} 实现。</p>
 */
public interface AsymmetricCipher extends Algorithm<AlgorithmFamily<? extends AsymmetricCipher>> {

    void init(boolean forEncryption, CipherParameters params);

    /**
     * 处理单个字节。
     *
     * @return 写入 {@code out} 的字节数
     */
    int processByte(byte in, byte[] out, int outOff);

    /**
     * 处理一段字节。
     *
     * @return 写入 {@code out} 的字节数
     */
    int processBytes(byte[] in, int inOff, int len, byte[] out, int outOff);

    void reset();
}
