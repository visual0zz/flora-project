package com.flora.crypto.core.interfaces.algorithm;

import com.flora.common.register.Algorithm;
import com.flora.common.register.AlgorithmFactory;
import com.flora.crypto.core.interfaces.material.keypair.AsymmetricCipherKeyPair;
import com.flora.crypto.core.interfaces.material.param.KeyGenerationParameter;

/**
 * 轻量级非对称密钥对生成器接口。
 * <p>返回本项目的 {@link AsymmetricCipherKeyPair}，与具体密钥格式解耦，适合在统一接口体系内生成密钥。</p>
 */
public interface AsymmetricCipherKeyPairGenerator
        extends Algorithm<AlgorithmFactory<? extends AsymmetricCipherKeyPairGenerator>> {

    void init(KeyGenerationParameter param);

    AsymmetricCipherKeyPair generateKeyPair();
}
