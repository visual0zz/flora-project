package com.flora.crypto.newcore.interfaces.provider.kem;

import com.flora.crypto.newcore.interfaces.param.CipherParameters;

/**
 * 密钥封装器（发送方用）。
 * <p>用接收方公钥封装出共享对称密钥与密文（encapsulation）。</p>
 */
public interface Encapsulator {

    /**
     * 执行封装。
     *
     * @return 封装结果（含共享密钥与密文）
     */
    SecretWithEncapsulation encapsulate();

    /** @return 接收方公钥参数（用于关联） */
    CipherParameters getPublicKey();
}
