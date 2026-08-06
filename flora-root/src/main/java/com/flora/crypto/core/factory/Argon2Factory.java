package com.flora.crypto.core.factory;

import com.flora.crypto.core.AlgorithmFactory;
import com.flora.crypto.core.impl.Argon2;

import java.util.Set;

/** Argon2 口令哈希 / KDF 工厂（大小写别名）。 */
public final class Argon2Factory implements AlgorithmFactory {

    @Override
    public Set<String> supportedAlgorithms() {
        return Set.of("Argon2", "ARGON2");
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
        return new Argon2();
    }
}
