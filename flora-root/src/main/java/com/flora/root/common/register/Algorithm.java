package com.flora.root.common.register;

public interface Algorithm<T extends AlgorithmFactory<?>> extends AlgorithmComponent{
    T factory();
    String getAlgorithmName();
}
