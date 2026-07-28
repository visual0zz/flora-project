package com.flora.crypto.core;

import com.flora.java.CheckUtil;

import java.security.SecureRandom;

/**
 * 轻量级密钥生成参数（Bouncy Castle 风格）。
 * <p>仅描述「强度（位数/字节数）+ 随机数源」，不绑定具体算法，由
 * {@link AsymmetricCipherKeyPairGenerator} 在 {@code init} 时按算法名解析。</p>
 */
public class KeyGenerationParameters {

    private final SecureRandom random;
    private final int strength;

    public KeyGenerationParameters(SecureRandom random, int strength) {
        CheckUtil.notNull(random, "随机数源不能为空");
        this.random = random;
        this.strength = strength;
    }

    public SecureRandom getRandom() {
        return random;
    }

    /** @return 密钥强度（EC 为位数，AES 为位数） */
    public int getStrength() {
        return strength;
    }
}
