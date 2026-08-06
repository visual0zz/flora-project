package com.flora.crypto.core.factory;

import com.flora.crypto.core.AlgorithmFactory;
import com.flora.crypto.core.bridge.JdkAsymmetricKeyPairGenerator;

import java.util.Set;

/**
 * JDK 非对称密钥对生成适配器工厂（返回 {@link com.flora.crypto.core.interfaces.provider.AsymmetricCipherKeyPairGenerator}）。
 * <p>为每个 JDK 支持的名字创建一个携带名字的实例；具体度覆写为支持集合大小。</p>
 */
public final class JdkAsymmetricKeyPairGeneratorFactory implements AlgorithmFactory {

    private String name;

    public JdkAsymmetricKeyPairGeneratorFactory() {}

    public JdkAsymmetricKeyPairGeneratorFactory(String name) {
        this.name = name;
    }

    @Override
    public void setAlgorithm(String name) {
        this.name = name;
    }

    @Override
    public Set<String> names() {
        return name == null ? JdkAsymmetricKeyPairGenerator.SUPPORTED : Set.of(name);
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public int specificity() {
        return JdkAsymmetricKeyPairGenerator.SUPPORTED.size();
    }

    @Override
    public Class<?>[] paramTypes() {
        return new Class<?>[0];
    }

    @Override
    public Object create(Object[] args) {
        return JdkAsymmetricKeyPairGenerator.of(name);
    }
}
