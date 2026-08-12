package com.flora.common.register;

public interface AlgorithmFactoryRegister {
    void register(AlgorithmFactory<?> factory);

    <F extends AlgorithmFactory<?>> F get(String name, Class<F> factoryType);

    /**
     * 通过 SPI（{@link java.util.ServiceLoader}）自动发现并注册所有自述为当前注册类的算法族。
     * <p>实现类应扫描 {@code AlgorithmFactory} 的 SPI 提供方，仅将 {@link AlgorithmFactory#registerTo()}
     * 指向当前注册类的那些算法族纳入注册。</p>
     */
    void registerBySpi();
}
