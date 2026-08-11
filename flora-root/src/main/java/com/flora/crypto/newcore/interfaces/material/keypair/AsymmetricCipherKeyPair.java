package com.flora.crypto.newcore.interfaces.material.keypair;

import com.flora.crypto.newcore.interfaces.material.param.CipherParameters;

/**
 * 非对称密钥对（与具体密钥格式解耦的轻量级表达）。
 */
public interface AsymmetricCipherKeyPair {

    /** @return 公钥参数 */
    CipherParameters getPublic();

    /** @return 私钥参数 */
    CipherParameters getPrivate();
}
