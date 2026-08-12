package com.flora.crypto.core.impl;

import com.flora.crypto.core.interfaces.material.keypair.AsymmetricCipherKeyPair;
import com.flora.crypto.core.interfaces.material.param.AsymmetricPrivateKeyParameter;
import com.flora.crypto.core.interfaces.material.param.AsymmetricPublicKeyParameter;
import com.flora.java.CheckUtil;

/**
 * {@link AsymmetricCipherKeyPair} 的默认实现，持有公钥/私钥参数。
 */
public final class AsymmetricCipherKeyPairImpl implements AsymmetricCipherKeyPair {

    private final AsymmetricPublicKeyParameter pub;
    private final AsymmetricPrivateKeyParameter priv;

    public AsymmetricCipherKeyPairImpl(AsymmetricPublicKeyParameter pub,
            AsymmetricPrivateKeyParameter priv) {
        CheckUtil.notNull(pub, "公钥参数不能为空");
        CheckUtil.notNull(priv, "私钥参数不能为空");
        this.pub = pub;
        this.priv = priv;
    }

    @Override
    public AsymmetricPublicKeyParameter getPublic() {
        return pub;
    }

    @Override
    public AsymmetricPrivateKeyParameter getPrivate() {
        return priv;
    }
}
