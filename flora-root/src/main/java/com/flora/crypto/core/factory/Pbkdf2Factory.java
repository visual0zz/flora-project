package com.flora.crypto.core.factory;

import com.flora.crypto.core.AlgorithmFactory;
import com.flora.crypto.core.impl.Pbkdf2DerivationFunction;
import com.flora.crypto.core.interfaces.provider.Mac;

import java.util.Set;

/** PBKDF2 派生函数工厂（以 {@link Mac} 为 PRF 的参数化组合算法）。 */
public final class Pbkdf2Factory implements AlgorithmFactory {

    @Override
    public Set<String> names() {
        return Set.of("PBKDF2");
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public Class<?>[] paramTypes() {
        return new Class<?>[]{Mac.class};
    }

    @Override
    public Object create(Object[] args) {
        return new Pbkdf2DerivationFunction((Mac) args[0]);
    }
}
