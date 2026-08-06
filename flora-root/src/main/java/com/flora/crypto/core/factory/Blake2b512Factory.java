package com.flora.crypto.core.factory;

import com.flora.crypto.core.AlgorithmFactory;
import com.flora.crypto.core.impl.Blake2bDigest;

import java.util.Set;

/** BLAKE2b-512 摘要工厂（固定 64 字节输出）。 */
public final class Blake2b512Factory implements AlgorithmFactory {

    @Override
    public Set<String> supportedAlgorithms() {
        return Set.of("BLAKE2B-512");
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
        return new Blake2bDigest(64);
    }
}
