package com.flora.crypto.core.factory;

import com.flora.crypto.core.AlgorithmFactory;
import com.flora.crypto.core.bridge.JdkMac;

import java.util.Set;

/**
 * JDK MAC 适配器工厂。
 * <p>为每个 JDK 支持的 MAC 名创建一个携带名字的实例；具体度覆写为支持集合大小。</p>
 */
public final class JdkMacFactory implements AlgorithmFactory {

    private String name;

    public JdkMacFactory() {}

    public JdkMacFactory(String name) {
        this.name = name;
    }

    @Override
    public void setAlgorithm(String name) {
        this.name = name;
    }

    @Override
    public Set<String> supportedAlgorithms() {
        return JdkMac.SUPPORTED;
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public int specificity() {
        return JdkMac.SUPPORTED.size();
    }

    @Override
    public Class<?>[] paramTypes() {
        return new Class<?>[0];
    }

    @Override
    public Object create(Object[] args) {
        return JdkMac.of(name);
    }
}
