package com.flora.crypto.core.factory;

import com.flora.crypto.core.AlgorithmFactory;
import com.flora.crypto.core.impl.Ripemd160Digest;

import java.util.Set;

/** RIPEMD-160 摘要工厂（大小写别名）。 */
public final class Ripemd160Factory implements AlgorithmFactory {

    @Override
    public Set<String> supportedAlgorithms() {
        return Set.of("Ripemd160", "RIPEMD160");
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public Class<?>[] paramTypes() {
        return new Class<?>[0];
    }

    @Override
    public Object create(Object[] args) {
        return new Ripemd160Digest();
    }
}
