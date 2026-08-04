package com.flora.crypto.core.factory;

import com.flora.crypto.core.AlgorithmFactory;
import com.flora.crypto.core.padding.ZeroBytePadding;

import java.util.Set;

/** 零字节填充工厂。 */
public final class ZeroByteFactory implements AlgorithmFactory {

    @Override
    public Set<String> names() {
        return Set.of("ZeroByte");
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
        return new ZeroBytePadding();
    }
}
