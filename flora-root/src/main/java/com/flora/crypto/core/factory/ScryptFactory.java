package com.flora.crypto.core.factory;

import com.flora.crypto.core.AlgorithmFactory;
import com.flora.crypto.core.impl.Scrypt;

import java.util.Set;

/** scrypt 口令哈希 / KDF 工厂（大小写别名）。 */
public final class ScryptFactory implements AlgorithmFactory {

    @Override
    public Set<String> supportedAlgorithms() {
        return Set.of("Scrypt", "SCRYPT");
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
        return new Scrypt();
    }
}
