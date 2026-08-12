package com.flora.root.crypto.core.interfaces.algorithm;

import com.flora.root.common.register.Algorithm;
import com.flora.root.common.register.AlgorithmFactory;
import com.flora.root.crypto.core.interfaces.material.param.CipherParameter;

/**
 * 非对称分组密码引擎接口（如 RSA）。
 * <p>加密/解密输入块大小受限（受密钥长度与填充开销影响），输出块大小固定为密钥字节长度。</p>
 */
public interface AsymmetricBlockCipher extends Algorithm<AlgorithmFactory<? extends AsymmetricBlockCipher>> {

    void init(boolean forEncryption, CipherParameter params);

    /** @return 单次加密允许的最大明文块长度（字节） */
    int getInputBlockSize();

    /** @return 输出密文块长度（字节） */
    int getOutputBlockSize();

    /**
     * 处理一个非对称块。
     *
     * @param in    输入
     * @param inOff 输入偏移
     * @param len   输入长度
     * @return 处理结果
     */
    byte[] processBlock(byte[] in, int inOff, int len);
}
