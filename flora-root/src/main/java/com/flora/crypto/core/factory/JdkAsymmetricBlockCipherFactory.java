package com.flora.crypto.core.factory;

import com.flora.crypto.core.AlgorithmFactory;
import com.flora.crypto.core.bridge.JdkAsymmetricBlockCipher;

import java.util.Set;

/**
 * JDK 裸 RSA 原语适配器工厂（不含填充，由组合层编排）。
 * <p>为每个 JDK 支持的非对称算法名创建一个携带名字的实例；具体度覆写为支持集合大小。</p>
 */
public final class JdkAsymmetricBlockCipherFactory implements AlgorithmFactory {

    private String name;


    @Override
    public void chooseAlgorithm(String name) {
        this.name = name;
    }

    @Override
    public Set<String> supportedAlgorithms() {
        return JdkAsymmetricBlockCipher.SUPPORTED;
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public int specificity() {
        return JdkAsymmetricBlockCipher.SUPPORTED.size();
    }

    @Override
    public Class<?>[] paramTypes() {
        return new Class<?>[0];
    }

    @Override
    public Object create(Object[] args) {
        return JdkAsymmetricBlockCipher.of(name);
    }
}
