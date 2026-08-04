package com.flora.crypto.core.keypair;
import com.flora.crypto.core.interfaces.CipherParameters;

import com.flora.java.CheckUtil;
import java.security.Key;

/**
 * 非对称密钥参数：持有 JDK 的 {@link Key}（公钥或私钥）。
 */
public final class AsymmetricKeyParameter implements CipherParameters {

    private final Key key;

    public AsymmetricKeyParameter(Key key) {
        CheckUtil.notNull(key, "密钥不能为空");
        this.key = key;
    }

    public Key getKey() {
        return key;
    }
}
