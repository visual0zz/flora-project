package com.flora.crypto.core.param;

import com.flora.crypto.core.interfaces.material.param.DerivationParameter;

import com.flora.java.CheckUtil;

/**
 * PBKDF2 口令派生参数（RFC 8018 §5.2）：口令、盐与迭代次数。
 */
public final class Pbkdf2Parameters implements DerivationParameter {

    private final byte[] password;
    private final byte[] salt;
    private final int iterationCount;

    public Pbkdf2Parameters(byte[] password, byte[] salt, int iterationCount) {
        CheckUtil.notNull(password, "口令不能为空");
        CheckUtil.notNull(salt, "盐不能为空");
        CheckUtil.mustTrue(iterationCount > 0, "迭代次数须为正");
        this.password = password.clone();
        this.salt = salt.clone();
        this.iterationCount = iterationCount;
    }

    public byte[] getPassword() {
        return password.clone();
    }

    public byte[] getSalt() {
        return salt.clone();
    }

    public int getIterationCount() {
        return iterationCount;
    }
}
