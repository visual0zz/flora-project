package com.flora.common.algorithm;

public interface Algorithm<T extends AlgorithmFactory<?>> extends AlgorithmComponent{
    T factory();
    String getAlgorithmName();
}
