package com.flora.crypto.core.factory;

import com.flora.crypto.core.AlgorithmFactory;
import com.flora.crypto.core.bridge.JdkKem;

import java.util.Set;

/**
 * JDK KEM 适配器工厂（后量子 ML-KEM，Java 21+）。
 * <p>为每个 JDK 支持的 KEM 名创建一个携带名字的实例；具体度覆写为支持集合大小。</p>
 */
public final class JdkKemFactory implements AlgorithmFactory {

    private String name;

    @Override
    public void chooseAlgorithm(String name) {
        this.name = name;
    }

    @Override
    public Set<String> supportedAlgorithms() {
        return JdkKem.SUPPORTED;
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public int specificity() {
        return JdkKem.SUPPORTED.size();
    }

    @Override
    public Class<?>[] paramTypes() {
        return new Class<?>[0];
    }

    @Override
    public Object create(Object[] args) {
        return JdkKem.of(name);
    }
}
