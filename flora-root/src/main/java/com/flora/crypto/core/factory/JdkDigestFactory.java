package com.flora.crypto.core.factory;

import com.flora.crypto.core.AlgorithmFactory;
import com.flora.crypto.core.bridge.JdkDigest;

import java.util.Set;

/**
 * JDK 摘要适配器工厂。
 * <p>为每个 JDK 支持的摘要名创建一个携带名字的实例；具体度覆写为支持集合大小，
 * 以保持「通用适配器让位于专用实现」的裁决语义。</p>
 */
public final class JdkDigestFactory implements AlgorithmFactory {

    private String name;

    public JdkDigestFactory() {}

    public JdkDigestFactory(String name) {
        this.name = name;
    }

    @Override
    public void setAlgorithm(String name) {
        this.name = name;
    }

    @Override
    public Set<String> names() {
        return name == null ? JdkDigest.SUPPORTED : Set.of(name);
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public int specificity() {
        return JdkDigest.SUPPORTED.size();
    }

    @Override
    public Class<?>[] paramTypes() {
        return new Class<?>[0];
    }

    @Override
    public Object create(Object[] args) {
        return JdkDigest.of(name);
    }
}
