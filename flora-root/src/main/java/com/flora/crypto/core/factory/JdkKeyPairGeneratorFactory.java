package com.flora.crypto.core.factory;

import com.flora.crypto.core.AlgorithmFactory;
import com.flora.crypto.core.bridge.JdkKeyPairGenerator;

import java.util.Set;

/**
 * JDK 密钥对生成适配器工厂（返回 {@link JdkKeyPairGenerator}）。
 * <p>为每个 JDK 支持的名字创建一个携带名字的实例；具体度覆写为支持集合大小。</p>
 */
public final class JdkKeyPairGeneratorFactory implements AlgorithmFactory {

    private String name;

    public JdkKeyPairGeneratorFactory() {}

    public JdkKeyPairGeneratorFactory(String name) {
        this.name = name;
    }

    @Override
    public void setAlgorithm(String name) {
        this.name = name;
    }

    @Override
    public Set<String> supportedAlgorithms() {
        return JdkKeyPairGenerator.SUPPORTED;
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public int specificity() {
        return JdkKeyPairGenerator.SUPPORTED.size();
    }

    @Override
    public Class<?>[] paramTypes() {
        return new Class<?>[0];
    }

    @Override
    public Object create(Object[] args) {
        return JdkKeyPairGenerator.of(name);
    }
}
