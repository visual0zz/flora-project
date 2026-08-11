package com.flora.crypto.schemes;

import com.flora.common.algorithm.AlgorithmComponent;
import com.flora.common.algorithm.AlgorithmFactory;
import com.flora.common.algorithm.AlgorithmFamilyRegister;
import com.flora.common.algorithm.UnregisteredAlgorithmException;
import com.flora.crypto.schemes.engine.kex.DhGroup14;
import com.flora.crypto.schemes.keyexchange.KeyExchange;

import com.flora.java.CheckUtil;
import com.flora.tag.ModuleEntry;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 方案注册与分发器。
 * <p>注册委托给 {@link SchemeAlgorithmFamilyRegister}（复用 common 的注册 / 归属校验 / 同名裁决 /
 * 按名查询能力）：算法族通过 {@link AlgorithmFactory#registerTo()} 自述注册到
 * {@link SchemeAlgorithmFamilyRegister}，经本类登记与分发。协议名与原语名属不同命名空间，分族持有注册表。</p>
 */
@ModuleEntry
public final class SchemeProvider {

    private SchemeProvider() {
    }

    /** 注册中心：每个实例即一个独立注册表。 */
    private static final SchemeAlgorithmFamilyRegister REGISTRY = new SchemeAlgorithmFamilyRegister();

    /** 已注册的方案名集合（供查询）。 */
    private static final Set<String> REGISTERED_NAMES = ConcurrentHashMap.newKeySet();

    static {
        // 内置密钥交换算法实现。调用方无需手动注册，直接经 keyExchange(name) 入口取实例。
        REGISTRY.register(DhGroup14.FACTORY);
        REGISTERED_NAMES.addAll(DhGroup14.FACTORY.supportedAlgorithms());
    }

    /**
     * 注册一个密钥交换算法实现（原型实例自述算法名与优先级）。
     *
     * @param prototype 原型实例（经 {@link KeyExchange} 自述支持的方案名）
     * @param factory   按方案名构造实例的工厂
     */
    public static void registerKeyExchange(KeyExchange prototype, Function<String, ? extends KeyExchange> factory) {
        if (!(prototype instanceof Scheme scheme)) {
            throw new IllegalArgumentException("原型实例必须实现 Scheme: " + prototype.getClass());
        }
        Set<String> algorithms = scheme.supportedAlgorithms();
        if (algorithms == null || algorithms.isEmpty()) {
            throw new IllegalArgumentException("supportedAlgorithms() 不能为空: " + prototype.getClass());
        }
        int priority = scheme.priority();
        AlgorithmFactory<? extends KeyExchange> adapter = new AlgorithmFactory<>() {
            @Override
            public Class<? extends AlgorithmFamilyRegister> registerTo() {
                return SchemeAlgorithmFamilyRegister.class;
            }

            @Override
            public Set<String> supportedAlgorithms() {
                return algorithms;
            }

            @Override
            public int priority() {
                return priority;
            }

            @Override
            public Class<? extends AlgorithmComponent>[] componentTypes() {
                return new Class[0];
            }

            @Override
            public KeyExchange construct(String algorithmName, AlgorithmComponent... components) {
                return factory.apply(algorithmName);
            }
        };
        REGISTRY.register(adapter);
        REGISTERED_NAMES.addAll(algorithms);
    }

    /**
     * 按方案名解析密钥交换算法。
     *
     * @param name 方案名（如 {@code "diffie-hellman-group14"}）
     * @return 算法实例
     */
    @SuppressWarnings("unchecked")
    public static KeyExchange keyExchange(String name) {
        CheckUtil.notEmpty(name, "方案名不能为空");
        try {
            AlgorithmFactory<?> factory = REGISTRY.get(name, AlgorithmFactory.class);
            return (KeyExchange) factory.construct(name, new AlgorithmComponent[0]);
        } catch (UnregisteredAlgorithmException e) {
            throw new IllegalArgumentException("未注册的方案名: " + name, e);
        }
    }

    /** @return 所有已注册的密钥交换方案名 */
    public static Set<String> keyExchangeAlgorithms() {
        return Set.copyOf(REGISTERED_NAMES);
    }
}
