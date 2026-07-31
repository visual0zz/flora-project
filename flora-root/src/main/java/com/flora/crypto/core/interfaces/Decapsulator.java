package com.flora.crypto.core.interfaces;

/**
 * KEM 解封装器（接收方侧）：从封装密文还原共享对称密钥。
 */
public interface Decapsulator {

    /** @return 期望的封装密文字节长度 */
    int getEncapsulationLength();

    /** @return 导出的共享密钥字节长度 */
    int getSecretLength();

    /**
     * 解封装。
     *
     * @param encapsulation 发送方给出的封装密文
     * @return 共享密钥与封装密文
     */
    SecretWithEncapsulation decapsulate(byte[] encapsulation);
}
