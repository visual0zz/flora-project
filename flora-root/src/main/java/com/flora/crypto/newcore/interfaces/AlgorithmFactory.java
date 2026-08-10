package com.flora.crypto.newcore.interfaces;

import com.flora.crypto.newcore.AlgorithmCategory;

import java.util.Set;

public interface AlgorithmFactory<T extends Algorithm<?>>{
    AlgorithmCategory category();

    Set<String> supportedAlgorithms();

    /** @return 自述优先级，越大越优先 */
    int priority();

    /**
     * 算法构造的时候需要注入的其他算法
     * 这个设计是为了实现灵活组合算法
     *
     * @return 非空数组表示该算法须
     */
    Class<? extends  Algorithm<?>>[] componentTypes();

    /** @return 将其他算法的实例注入算法来进行初始化 */
    T construct(String algorithmName, Algorithm<?>[] components);
}
