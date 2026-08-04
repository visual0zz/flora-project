package com.flora.crypto.core.impl;
import com.flora.crypto.core.interfaces.SecretWithEncapsulation;

import com.flora.java.CheckUtil;

import java.util.Arrays;

/**
 * {@link SecretWithEncapsulation} 的默认实现。
 */
public final class SecretWithEncapsulationImpl implements SecretWithEncapsulation {

    private byte[] secret;
    private final byte[] encapsulation;

    public SecretWithEncapsulationImpl(byte[] secret, byte[] encapsulation) {
        CheckUtil.notNull(secret, "共享密钥不能为空");
        CheckUtil.notNull(encapsulation, "封装密文不能为空");
        this.secret = secret.clone();
        this.encapsulation = encapsulation.clone();
    }

    @Override
    public byte[] getSecret() {
        return secret != null ? secret.clone() : new byte[0];
    }

    @Override
    public byte[] getEncapsulation() {
        return encapsulation.clone();
    }

    @Override
    public void destroy() {
        if (secret != null) {
            Arrays.fill(secret, (byte) 0);
            secret = null;
        }
    }
}
