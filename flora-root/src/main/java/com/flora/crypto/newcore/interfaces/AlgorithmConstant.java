package com.flora.crypto.newcore.interfaces;

public interface AlgorithmConstant<T> extends AlgorithmComponent{
    T getValue();
    Class<T> getType();
}
