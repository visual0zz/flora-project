package com.flora.crypto.core.param;
import com.flora.crypto.core.interfaces.CipherParameters;

import com.flora.java.CheckUtil;

/**
 * 对称密钥参数：仅持有原始密钥字节。
 */
public final class KeyParameter implements CipherParameters {

    private final byte[] key;

    public KeyParameter(byte[] key) {
        CheckUtil.notNull(key, "密钥不能为空");
        this.key = key.clone();
    }

    public byte[] getKey() {
        return key.clone();
    }
}
