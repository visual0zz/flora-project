package com.flora.crypto.core;

import com.flora.java.CheckUtil;

/**
 * 基于摘要的 KDF 参数（如 KDF1/KDF2），持有共享秘密 Z 与可选的共享信息（shared info）。
 */
public final class KdfParameters implements DerivationParameters {

    private final byte[] shared;
    private final byte[] iv;

    public KdfParameters(byte[] shared, byte[] iv) {
        CheckUtil.notNull(shared, "共享秘密不能为空");
        this.shared = shared.clone();
        this.iv = iv != null ? iv.clone() : null;
    }

    public byte[] getShared() {
        return shared.clone();
    }

    public byte[] getIV() {
        return iv != null ? iv.clone() : null;
    }
}
