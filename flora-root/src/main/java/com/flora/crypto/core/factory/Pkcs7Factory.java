package com.flora.crypto.core.factory;

import com.flora.crypto.core.AlgorithmFactory;
import com.flora.crypto.core.padding.PKCS7Padding;

import java.util.Set;

/** PKCS#7 / PKCS#5 填充工厂（别名）。 */
public final class Pkcs7Factory implements AlgorithmFactory {

    @Override
    public Set<String> names() {
        return Set.of("PKCS7", "PKCS5");
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
        return new PKCS7Padding();
    }
}
