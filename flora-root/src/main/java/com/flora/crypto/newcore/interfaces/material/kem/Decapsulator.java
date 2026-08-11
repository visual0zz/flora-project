package com.flora.crypto.newcore.interfaces.material.kem;

import com.flora.crypto.newcore.interfaces.material.param.AsymmetricPrivateKeyParameter;
import com.flora.crypto.newcore.interfaces.material.param.CipherParameter;

/**
 * 密钥解封装器（接收方用）。
 * <p>用接收方私钥从密文还原出与发送方一致的共享对称密钥。</p>
 */
public interface Decapsulator {

    /**
     * 执行解封装。
     *
     * @param encapsulation 发送方给出的密文
     * @return 共享对称密钥
     */
    byte[] decapsulate(byte[] encapsulation);

    /** @return 接收方私钥参数（用于关联） */
    AsymmetricPrivateKeyParameter getPrivateKey();
}
