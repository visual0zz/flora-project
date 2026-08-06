package com.flora.crypto.core.factory;

import com.flora.crypto.core.AlgorithmFactory;
import com.flora.crypto.core.impl.Blake2bDigest;

import java.util.Set;

/** BLAKE2b 摘要工厂（带输出长度参数 {@code integer:N}）。 */
public final class Blake2bFactory implements AlgorithmFactory {

    @Override
    public Set<String> supportedAlgorithms() {
        return Set.of("Blake2b");
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public Class<?>[] paramTypes() {
        return new Class<?>[]{Integer.class};
    }

    @Override
    public Object create(Object[] args) {
        return new Blake2bDigest((Integer) args[0]);
    }
}
