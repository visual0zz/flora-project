package com.flora.crypto.core.factory;

import com.flora.crypto.core.AlgorithmFactory;
import com.flora.crypto.core.interfaces.provider.BlockCipher;
import com.flora.crypto.core.mode.GCMBlockCipher;

import java.util.Set;

/** GCM 分组密码模式工厂（以底层 {@link BlockCipher} 为参数的组合算法，提供 AEAD）。 */
public final class GcmFactory implements AlgorithmFactory {

    @Override
    public Set<String> supportedAlgorithms() {
        return Set.of("GCM");
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public Class<?>[] paramTypes() {
        return new Class<?>[]{BlockCipher.class};
    }

    @Override
    public Object create(Object[] args) {
        return new GCMBlockCipher((BlockCipher) args[0]);
    }
}
