package com.flora.entropy.compress;

import com.flora.common.algorithm.AlgorithmComponent;
import com.flora.common.algorithm.AlgorithmFactory;
import com.flora.common.algorithm.AlgorithmFamilyRegister;
import com.flora.common.algorithm.UnregisteredAlgorithmException;
import com.flora.entropy.compress.engine.DeflateCompressor;
import com.flora.java.CheckUtil;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 压缩组件注册表（复用 common 的注册机制）。
 * <p>注册委托给 {@link CompressorAlgorithmFamilyRegister}（复用 common 的注册 / 归属校验 / 同名裁决 /
 * 按名查询能力）：实现类通过 {@link AlgorithmFactory} 自述支持的算法集合与优先级，
 * 由本类按算法名注册与分发，分发时按「能实现 → 优先级（越大越优先）→ 具体度（算法数越少越优先）」裁决。</p>
 *
 * <pre>{@code
 * Compressor c = CompressorProvider.compressor("DEFLATE");
 * byte[] compressed = c.compress(data);
 * byte[] original = c.decompress(compressed);
 * }</pre>
 *
 * <h2>自定义算法注册</h2>
 * <pre>{@code
 * CompressorProvider.register(new MyCompressor(), MyCompressor::of);
 * Compressor c = CompressorProvider.compressor("LZ4");
 * }</pre>
 */
public final class CompressorProvider {

    private CompressorProvider() {
    }

    // ── 注册表：算法名 → 工厂（common 注册中心） ──

    /** 注册中心：每个实例即一个独立注册表。 */
    private static final CompressorAlgorithmFamilyRegister REGISTRY = new CompressorAlgorithmFamilyRegister();

    /** 已注册的算法名集合（供查询）。 */
    private static final Set<String> REGISTERED_NAMES = ConcurrentHashMap.newKeySet();

    /** 记录每个原型类声明的算法名，用于 {@link #registeredCompressors()} 查询。 */
    private static final Map<Class<? extends Compressor>, Set<String>> REGISTERED_PROTOTYPES = new LinkedHashMap<>();

    static {
        REGISTRY.register(DeflateCompressor.FACTORY);
        track(DeflateCompressor.class, DeflateCompressor.FACTORY.supportedAlgorithms());
    }

    // ── 注册入口 ──

    /**
     * 注册压缩算法（原型实例自述支持的算法集合与优先级）。
     *
     * @param prototype 原型实例（经 {@link Compressor} 自述支持的算法名）
     * @param factory   按算法名创建新实例的工厂
     */
    public static void registerCompressor(Compressor prototype, Function<String, ? extends Compressor> factory) {
        Set<String> algorithms = prototype.supportedAlgorithms();
        if (algorithms == null || algorithms.isEmpty()) {
            throw new IllegalArgumentException("supportedAlgorithms() 不能为空: " + prototype.getClass());
        }
        int priority = prototype.priority();
        AlgorithmFactory<? extends Compressor> adapter = new AlgorithmFactory<>() {
            @Override
            public Class<? extends AlgorithmFamilyRegister> registerTo() {
                return CompressorAlgorithmFamilyRegister.class;
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
            public Class<AlgorithmComponent>[] componentTypes() {
                return new Class[0];
            }

            @Override
            public Compressor construct(String algorithmName, AlgorithmComponent... components) {
                return factory.apply(algorithmName);
            }
        };
        REGISTRY.register(adapter);
        track(prototype.getClass(), algorithms);
    }

    /** 收集已注册算法名与原型类映射（供查询入口使用）。 */
    private static void track(Class<? extends Compressor> clazz, Set<String> algorithms) {
        REGISTERED_NAMES.addAll(algorithms);
        synchronized (REGISTERED_PROTOTYPES) {
            REGISTERED_PROTOTYPES.computeIfAbsent(clazz, k -> new LinkedHashSet<>()).addAll(algorithms);
        }
    }

    // ── 查询 ──

    /**
     * 返回当前已注册的所有压缩算法名（不可变视图）。
     *
     * @return 算法名集合
     */
    public static Set<String> registeredAlgorithms() {
        return Collections.unmodifiableSet(REGISTERED_NAMES);
    }

    /**
     * 按注册原型类列出每个实现类所支持的算法名。
     * <p>返回的 Map 键为注册时传入的原型实例的 {@code Class}，
     * 值为该实现声明的算法名集合（不可变视图）。</p>
     *
     * @return 实现类 → 算法名的不可变映射
     */
    public static synchronized Map<Class<? extends Compressor>, Set<String>> registeredCompressors() {
        Map<Class<? extends Compressor>, Set<String>> result = new LinkedHashMap<>();
        for (var entry : REGISTERED_PROTOTYPES.entrySet()) {
            result.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    // ── 分发入口 ──

    /**
     * 按算法名获取压缩器实例。
     *
     * @param name 算法名（如 {@code "DEFLATE"}）
     * @return 压缩器实例
     * @throws com.flora.common.algorithm.UnregisteredAlgorithmException 若算法未注册
     */
    @SuppressWarnings("unchecked")
    public static Compressor compressor(String name) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        try {
            AlgorithmFactory<?> factory = REGISTRY.get(name, AlgorithmFactory.class);
            return (Compressor) factory.construct(name, new AlgorithmComponent[0]);
        } catch (UnregisteredAlgorithmException e) {
            throw new IllegalArgumentException("未注册的压缩算法: " + name, e);
        }
    }
}
