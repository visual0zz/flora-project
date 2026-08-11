package com.flora.common.algorithm;

public interface Algorithm<T extends AlgorithmFamily<?>> extends AlgorithmComponent{
    T factory();
    String getAlgorithmName();
}
