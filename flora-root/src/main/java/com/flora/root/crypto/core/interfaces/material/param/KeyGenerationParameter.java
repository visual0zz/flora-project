package com.flora.root.crypto.core.interfaces.material.param;

/**
 * 密钥生成参数根接口。
 * <p>具体形态由生成器表达：密钥长度、曲线/群参数、随机数源等。</p>
 */
public interface KeyGenerationParameter extends CipherParameter {

    /** @return 期望的安全强度（比特），如 128 / 256 */
    int getStrength();
}
