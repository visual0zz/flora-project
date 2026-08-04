package com.flora.crypto.core.factory;

import com.flora.crypto.core.AlgorithmFactory;
import com.flora.crypto.core.Kdf2DerivationFunction;
import com.flora.crypto.core.interfaces.provider.Digest;

import java.util.Set;

/** KDF2 派生函数工厂（以 {@link Digest} 为原语的参数化组合算法）。 */
public final class Kdf2Factory implements AlgorithmFactory {

    @Override
    public Set<String> names() {
        return Set.of("KDF2");
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public Class<?>[] paramTypes() {
        return new Class<?>[]{Digest.class};
    }

    @Override
    public Object create(Object[] args) {
        return new Kdf2DerivationFunction((Digest) args[0]);
    }
}
