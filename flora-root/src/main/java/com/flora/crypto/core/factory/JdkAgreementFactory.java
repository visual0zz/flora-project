package com.flora.crypto.core.factory;

import com.flora.crypto.core.AlgorithmFactory;
import com.flora.crypto.core.bridge.JdkAgreement;

import java.util.Set;

/**
 * JDK 密钥协商适配器工厂。
 * <p>为每个 JDK 支持的协商名创建一个携带名字的实例；具体度覆写为支持集合大小。</p>
 */
public final class JdkAgreementFactory implements AlgorithmFactory {

    private final String name;

    public JdkAgreementFactory(String name) {
        this.name = name;
    }

    @Override
    public Set<String> names() {
        return Set.of(name);
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public int specificity() {
        return JdkAgreement.SUPPORTED.size();
    }

    @Override
    public Class<?>[] paramTypes() {
        return new Class<?>[0];
    }

    @Override
    public Object create(Object[] args) {
        return JdkAgreement.of(name);
    }
}
