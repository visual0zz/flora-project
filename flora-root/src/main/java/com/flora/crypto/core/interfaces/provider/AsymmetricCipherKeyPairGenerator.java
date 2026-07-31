package com.flora.crypto.core.interfaces.provider;
import com.flora.crypto.core.AsymmetricCipherKeyPair;
import com.flora.crypto.core.KeyGenerationParameters;

/**
 * 轻量级非对称密钥对生成器接口（Bouncy Castle 风格）。
 * <p>与 JDK 的 {@code KeyPairGenerator} 解耦：返回本项目的 {@link AsymmetricCipherKeyPair}，
 * 由 {@code JdkAsymmetricKeyPairGenerator} 适配 JDK。适合在 BC 式接口体系内生成密钥。</p>
 */
public interface AsymmetricCipherKeyPairGenerator extends AlgorithmFamily {

    void init(KeyGenerationParameters param);

    AsymmetricCipherKeyPair generateKeyPair();
}
