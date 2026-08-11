package com.flora.crypto.newcore.interfaces.material.keypair;

import com.flora.crypto.newcore.interfaces.material.param.AsymmetricPrivateKeyParameter;
import com.flora.crypto.newcore.interfaces.material.param.AsymmetricPublicKeyParameter;

/**
 * 非对称密钥对（与具体密钥格式解耦的轻量级表达）。
 * <p>公钥/私钥分别返回对应子类型，避免调用方把公私钥混用。</p>
 */
public interface AsymmetricCipherKeyPair {

    /** @return 公钥参数 */
    AsymmetricPublicKeyParameter getPublic();

    /** @return 私钥参数 */
    AsymmetricPrivateKeyParameter getPrivate();
}
