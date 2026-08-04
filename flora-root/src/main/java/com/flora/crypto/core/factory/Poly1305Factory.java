package com.flora.crypto.core.factory;

import com.flora.crypto.core.AlgorithmFactory;
import com.flora.crypto.core.impl.Poly1305Mac;

import java.util.Set;

/** Poly1305 MAC 工厂（大小写别名）。 */
public final class Poly1305Factory implements AlgorithmFactory {

    @Override
    public Set<String> names() {
        return Set.of("Poly1305", "POLY1305");
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
        return new Poly1305Mac();
    }
}
