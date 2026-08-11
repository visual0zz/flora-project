package com.flora.crypto.newcore.impl;

import com.flora.crypto.newcore.interfaces.material.param.KeyGenerationParameter;
import com.flora.java.CheckUtil;

import java.security.SecureRandom;

/**
 * 非对称密钥生成参数：安全强度（比特）+ 可选随机数源。
 */
public final class KeyGenerationParameterImpl implements KeyGenerationParameter {

    private final int strength;
    private final SecureRandom random;

    public KeyGenerationParameterImpl(int strength) {
        this(strength, new SecureRandom());
    }

    public KeyGenerationParameterImpl(int strength, SecureRandom random) {
        CheckUtil.mustTrue(strength > 0, "安全强度须为正");
        CheckUtil.notNull(random, "随机数源不能为空");
        this.strength = strength;
        this.random = random;
    }

    @Override
    public int getStrength() {
        return strength;
    }

    public SecureRandom getRandom() {
        return random;
    }
}
