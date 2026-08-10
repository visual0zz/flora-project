package com.flora.crypto.newcore.interfaces.provider.kem;

import com.flora.crypto.newcore.interfaces.param.CipherParameters;

/**
 * 封装产物：共享对称密钥 + 随附密文（encapsulation）。
 */
public interface SecretWithEncapsulation {

    /** @return 共享对称密钥 */
    byte[] getSecret();

    /** @return 随附密文（发送方需传给接收方） */
    byte[] getEncapsulation();

    /** @return 该密钥的算法名 / 标识 */
    String getAlgorithmName();

    /** 释放底层密钥材料（敏感内存清理）。 */
    void destroy();
}
