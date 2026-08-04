package com.flora.crypto.core.factory;

import com.flora.crypto.core.AlgorithmFactory;
import com.flora.crypto.core.padding.ISO7816d4Padding;

import java.util.Set;

/** ISO/IEC 7816-4 填充工厂（别名）。 */
public final class Iso7816Factory implements AlgorithmFactory {

    @Override
    public Set<String> names() {
        return Set.of("ISO7816", "ISO7816-4");
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
        return new ISO7816d4Padding();
    }
}
