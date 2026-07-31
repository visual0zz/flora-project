package com.flora.crypto.core;
import com.flora.crypto.core.interfaces.CipherParameters;

import com.flora.java.CheckUtil;
import java.security.SecureRandom;

/**
 * 携带随机数源的参数包装。随机数源用于密钥生成、签名等需要随机性的场景。
 */
public final class ParametersWithRandom implements CipherParameters {

    private final CipherParameters parameters;
    private final SecureRandom random;

    public ParametersWithRandom(CipherParameters parameters, SecureRandom random) {
        CheckUtil.notNull(parameters, "参数不能为空");
        this.parameters = parameters;
        this.random = random != null ? random : new SecureRandom();
    }

    public CipherParameters getParameters() {
        return parameters;
    }

    public SecureRandom getRandom() {
        return random;
    }
}
