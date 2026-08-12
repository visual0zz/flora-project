package com.flora.crypto.core.param;

import com.flora.crypto.core.interfaces.material.param.DerivationParameter;

import com.flora.java.CheckUtil;

/**
 * HKDF 参数，持有伪随机密钥（PRK）与可选的 info。
 */
public final class HkdfParameters implements DerivationParameter {

    private final byte[] key;
    private final byte[] info;

    public HkdfParameters(byte[] key, byte[] info) {
        CheckUtil.notNull(key, "伪随机密钥不能为空");
        this.key = key.clone();
        this.info = info != null ? info.clone() : null;
    }

    public byte[] getKey() {
        return key.clone();
    }

    public byte[] getInfo() {
        return info != null ? info.clone() : null;
    }
}
