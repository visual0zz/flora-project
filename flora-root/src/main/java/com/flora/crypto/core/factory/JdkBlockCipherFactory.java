package com.flora.crypto.core.factory;

import com.flora.crypto.core.AlgorithmFactory;
import com.flora.crypto.core.bridge.JdkBlockCipher;

import java.util.Set;

/**
 * JDK 分组密码适配器工厂。
 * <p>为每个 JDK 支持的分组密码名创建一个携带名字的实例；具体度覆写为支持集合大小。</p>
 */
public final class JdkBlockCipherFactory implements AlgorithmFactory {

    private String name;

    @Override
    public void chooseAlgorithm(String name) {
        this.name = name;
    }

    @Override
    public Set<String> supportedAlgorithms() {
        return JdkBlockCipher.SUPPORTED;
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public int specificity() {
        return JdkBlockCipher.SUPPORTED.size();
    }

    @Override
    public Class<?>[] paramTypes() {
        return new Class<?>[0];
    }

    @Override
    public Object create(Object[] args) {
        return JdkBlockCipher.of(name);
    }
}
