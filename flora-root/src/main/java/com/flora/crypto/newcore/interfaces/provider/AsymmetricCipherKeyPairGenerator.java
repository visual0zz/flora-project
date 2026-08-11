package com.flora.crypto.newcore.interfaces.provider;

import com.flora.common.algorithm.Algorithm;
import com.flora.common.algorithm.AlgorithmFactory;
import com.flora.crypto.newcore.interfaces.param.KeyGenerationParameters;
import com.flora.crypto.newcore.interfaces.keypair.AsymmetricCipherKeyPair;

/**
 * 轻量级非对称密钥对生成器接口。
 * <p>返回本项目的 {@link AsymmetricCipherKeyPair}，与具体密钥格式解耦，适合在统一接口体系内生成密钥。</p>
 */
public interface AsymmetricCipherKeyPairGenerator
        extends Algorithm<AlgorithmFactory<? extends AsymmetricCipherKeyPairGenerator>> {

    void init(KeyGenerationParameters param);

    AsymmetricCipherKeyPair generateKeyPair();
}
