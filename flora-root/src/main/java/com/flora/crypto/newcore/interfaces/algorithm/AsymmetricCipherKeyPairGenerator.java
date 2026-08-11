package com.flora.crypto.newcore.interfaces.algorithm;

import com.flora.common.algorithm.Algorithm;
import com.flora.common.algorithm.AlgorithmFamily;
import com.flora.crypto.newcore.interfaces.material.keypair.AsymmetricCipherKeyPair;
import com.flora.crypto.newcore.interfaces.material.param.KeyGenerationParameters;

/**
 * 轻量级非对称密钥对生成器接口。
 * <p>返回本项目的 {@link AsymmetricCipherKeyPair}，与具体密钥格式解耦，适合在统一接口体系内生成密钥。</p>
 */
public interface AsymmetricCipherKeyPairGenerator
        extends Algorithm<AlgorithmFamily<? extends AsymmetricCipherKeyPairGenerator>> {

    void init(KeyGenerationParameters param);

    AsymmetricCipherKeyPair generateKeyPair();
}
