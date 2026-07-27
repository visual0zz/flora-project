package com.flora.crypto.core;

import com.flora.java.CheckUtil;

/**
 * 携带初始化向量（IV）的参数包装，用于 CBC / CTR / GCM / OFB / CFB 等需要 IV 的算法。
 */
public final class ParametersWithIV implements CipherParameters {

    private final CipherParameters parameters;
    private final byte[] iv;

    public ParametersWithIV(CipherParameters parameters, byte[] iv) {
        CheckUtil.notNull(iv, "IV 不能为空");
        this.parameters = parameters;
        this.iv = iv.clone();
    }

    public CipherParameters getParameters() {
        return parameters;
    }

    public byte[] getIV() {
        return iv.clone();
    }
}
