package com.flora.common.algorithm;

public interface AlgorithmRegistry {
    void register(AlgorithmFactory<?> factory);
    <F extends AlgorithmFactory<?>> F get(String name, Class<F> factoryType);
}
