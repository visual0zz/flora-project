package com.flora.crypto.core;

/**
 * KEM 封装器（发送方侧）：生成共享对称密钥与封装密文。
 */
public interface Encapsulator {

    /** @return 封装密文（encapsulation）的字节长度 */
    int getEncapsulationLength();

    /** @return 导出的共享密钥字节长度 */
    int getSecretLength();

    /** @return 共享密钥与封装密文 */
    SecretWithEncapsulation encapsulate();
}
