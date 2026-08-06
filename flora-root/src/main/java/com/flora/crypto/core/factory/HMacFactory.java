package com.flora.crypto.core.factory;

import com.flora.crypto.core.AlgorithmFactory;
import com.flora.crypto.core.impl.HMac;
import com.flora.crypto.core.interfaces.provider.ExtendedDigest;

import java.util.Set;

/** HMAC 工厂（以 {@link ExtendedDigest} 为哈希原语的参数化组合算法）。 */
public final class HMacFactory implements AlgorithmFactory {

    @Override
    public Set<String> supportedAlgorithms() {
        return Set.of("HMac");
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public Class<?>[] paramTypes() {
        return new Class<?>[]{ExtendedDigest.class};
    }

    @Override
    public Object create(Object[] args) {
        return new HMac((ExtendedDigest) args[0]);
    }
}
