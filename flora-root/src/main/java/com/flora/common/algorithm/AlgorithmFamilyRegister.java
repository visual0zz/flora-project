package com.flora.common.algorithm;

public interface AlgorithmFamilyRegister {
    void register(AlgorithmFamily<?> factory);
    <F extends AlgorithmFamily<?>> F get(String name, Class<F> factoryType);
}
