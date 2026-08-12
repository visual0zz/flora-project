package com.flora.root.crypto.core.interfaces.material.param;

import com.flora.root.java.CheckUtil;

/**
 * 对称密钥参数：仅持有原始密钥字节。
 */
public final class KeyParameterImpl implements KeyParameter {

    private final byte[] key;

    public KeyParameterImpl(byte[] key) {
        CheckUtil.notNull(key, "密钥不能为空");
        this.key = key.clone();
    }

    @Override
    public byte[] getKey() {
        return key.clone();
    }
}
