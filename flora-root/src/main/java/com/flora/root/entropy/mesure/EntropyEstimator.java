package com.flora.root.entropy.mesure;

import com.flora.root.common.register.*;
import com.flora.root.common.register.AlgorithmComponent;
import com.flora.root.common.register.AlgorithmFactory;
import com.flora.root.common.register.AlgorithmFactoryRegister;
import com.flora.root.common.register.UnregisteredAlgorithmException;
import com.flora.root.entropy.mesure.engine.BaseAlphabetEntropy;
import com.flora.root.entropy.mesure.engine.ComplexityRatio;
import com.flora.root.entropy.mesure.engine.EnglishMarkovEntropy;
import com.flora.root.entropy.mesure.engine.ShannonEntropy;
import com.flora.root.java.CheckUtil;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 熵度量算法注册表与归一化汇总层（复用 common 的注册机制）。
 * <p>注册委托给 {@link EntropyMetricAlgorithmFactoryRegister}（复用 common 的注册 / 归属校验 / 同名裁决 /
 * 按名查询能力）：实现类通过 {@link AlgorithmFactory} 自述支持的算法集合与优先级，
 * 由本类按算法名注册与分发，分发时按「能实现 → 优先级（越大越优先）→ 具体度（算法数越少越优先）」裁决。</p>
 *
 * <p>算法只实现 {@code measure(byte[])} 输出熵总量；本层负责<b>密度归一化</b>：
 * 熵上限按输入字节长度推导（{@link #maxPerByte(int)}），密度 = 熵总量 / 上限；
 * 聚合取所有算法密度的最小值（{@link #minDensity}）即「最保守算法」的判定。</p>
 *
 * <pre>{@code
 * EntropyMetric metric = EntropyEstimator.metric("SHANNON");
 * double entropy = metric.measure(data);
 * double density = EntropyEstimator.density("SHANNON", data);
 * double minD = EntropyEstimator.minDensity(data);
 * }</pre>
 *
 * <h2>自定义算法注册</h2>
 * <pre>{@code
 * EntropyEstimator.registerMetric(new MyEntropyMetric(), name -> new MyEntropyMetric());
 * EntropyMetric m = EntropyEstimator.metric("MY_METRIC");
 * }</pre>
 */
public final class EntropyEstimator {

    private EntropyEstimator() {
    }

    // ── 注册表：算法名 → 工厂（common 注册中心） ──

    /** 注册中心：每个实例即一个独立注册表。 */
    private static final EntropyMetricAlgorithmFactoryRegister REGISTRY = new EntropyMetricAlgorithmFactoryRegister();

    /** 已注册的算法名集合（供查询）。 */
    private static final Set<String> REGISTERED_NAMES = ConcurrentHashMap.newKeySet();

    /** 记录每个原型类声明的算法名，用于 {@link #registeredMetrics()} 查询。 */
    private static final Map<Class<? extends EntropyMetric>, Set<String>> REGISTERED_PROTOTYPES = new LinkedHashMap<>();

    /**
     * 默认聚合的核心算法集合。
     * <p>仅包含有独立贡献的算法：{@code SHANNON}（分布熵）与 {@code COMPLEXITY_RATIO}
     * （压缩不可压缩度，独立视角）。base 编码与英文马尔可夫等算法的度量要么与香农熵
     * 同构（纯编码数据下相等）、要么恒不小于香农熵（交叉熵性质），在取最小值的聚合中
     * 不会成为决定性因子，故不作为默认成员——需要时在 {@code minDensity} 显式指定。</p>
     */
    private static final Set<String> DEFAULT_AGGREGATION = Set.of("SHANNON", "COMPLEXITY_RATIO");

    static {
        REGISTRY.register(ShannonEntropy.FACTORY);
        REGISTRY.register(ComplexityRatio.FACTORY);
        // 常见 base 编码字符熵：BASE16/64/64URL，一个原型声明全部算法名，按名创建实例
        REGISTRY.register(BaseAlphabetEntropy.FACTORY);
        // 一阶英文马尔可夫（与英文的交叉熵，同为每字节熵，统一参与聚合）
        REGISTRY.register(EnglishMarkovEntropy.FACTORY);
        track(ShannonEntropy.class, ShannonEntropy.FACTORY.supportedAlgorithms());
        track(ComplexityRatio.class, ComplexityRatio.FACTORY.supportedAlgorithms());
        track(BaseAlphabetEntropy.class, BaseAlphabetEntropy.FACTORY.supportedAlgorithms());
        track(EnglishMarkovEntropy.class, EnglishMarkovEntropy.FACTORY.supportedAlgorithms());
    }

    // ── 注册入口 ──

    /**
     * 注册熵度量算法（原型实例自述支持的算法集合与优先级）。
     *
     * @param prototype 原型实例（经 {@link EntropyMetric} 自述支持的算法名）
     * @param factory   按算法名创建新实例的工厂
     */
    public static void registerMetric(EntropyMetric prototype, Function<String, ? extends EntropyMetric> factory) {
        Set<String> algorithms = prototype.supportedAlgorithms();
        if (algorithms == null || algorithms.isEmpty()) {
            throw new IllegalArgumentException("supportedAlgorithms() 不能为空: " + prototype.getClass());
        }
        int priority = prototype.priority();
        AlgorithmFactory<? extends EntropyMetric> adapter = new AlgorithmFactory<>() {
            @Override
            public Class<? extends AlgorithmFactoryRegister> registerTo() {
                return EntropyMetricAlgorithmFactoryRegister.class;
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
            public EntropyMetric construct(String algorithmName, AlgorithmComponent... components) {
                return factory.apply(algorithmName);
            }
        };
        REGISTRY.register(adapter);
        track(prototype.getClass(), algorithms);
    }

    /** 收集已注册算法名与原型类映射（供查询入口使用）。 */
    private static void track(Class<? extends EntropyMetric> clazz, Set<String> algorithms) {
        REGISTERED_NAMES.addAll(algorithms);
        synchronized (REGISTERED_PROTOTYPES) {
            REGISTERED_PROTOTYPES.computeIfAbsent(clazz, k -> new LinkedHashSet<>()).addAll(algorithms);
        }
    }

    // ── 分发入口 ──

    /**
     * 按算法名获取度量实例。
     *
     * @param name 算法名（如 {@code "SHANNON"}、{@code "COMPLEXITY_RATIO"}）
     * @return 度量实例
     * @throws IllegalArgumentException 若算法未注册
     */
    @SuppressWarnings("unchecked")
    public static EntropyMetric metric(String name) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        try {
            AlgorithmFactory<?> factory = REGISTRY.get(name, AlgorithmFactory.class);
            return (EntropyMetric) factory.construct(name, new AlgorithmComponent[0]);
        } catch (UnregisteredAlgorithmException e) {
            throw new IllegalArgumentException("未注册的熵度量算法: " + name, e);
        }
    }

    // ── 上限与归一化 ──

    /**
     * 按输入字节长度推导的<b>每字节熵上限</b>（bit/字节）。
     * <p>{@code N} 字节数据最多出现 {@code N} 个不同字节值（受 256 上限约束），
     * 均匀分布时的每字节熵即 {@code log2(min(N, 256))}。{@code N <= 1} 时无法评估，返回 0。</p>
     *
     * @param n 输入字节长度
     * @return 每字节熵上限，范围 {@code [0,8]}
     */
    public static double maxPerByte(int n) {
        if (n <= 1) {
            return 0.0;
        }
        return Math.log(Math.min(n, 256)) / Math.log(2);
    }

    /**
     * 计算单算法对字节数据的<b>随机性密度</b>（{@code [0,1]}）：
     * 熵总量除以按字节长度推导的上限，越接近 1 越像随机数据。
     *
     * @param name 算法名
     * @param data 待评估字节数组，{@code null} 或空数组返回 0
     * @return 随机性密度，范围 {@code [0,1]}
     * @throws IllegalArgumentException 若算法名未注册
     */
    public static double density(String name, byte[] data) {
        double measure = metric(name).measure(data);
        double max = maxPerByte(data == null ? 0 : data.length);
        if (max <= 0) {
            return 0.0;
        }
        return Math.min(measure / max, 1.0);
    }

    /**
     * 计算输入字节的<b>压缩复杂度比</b>（压缩后长度 / 原长，范围 {@code [0,1]}）。
     * <p>是 {@code COMPLEXITY_RATIO} 算法的「可压缩度」语义视图，与 {@link #density(String, byte[])}
     * 返回的<b>归一化熵密度</b>（按字节长度上限归一）不同：本方法直接反映压缩比本身，
     * 不受 {@code maxPerByte(n)} 随短数据缩小的上界影响。</p>
     *
     * @param data 待评估字节数组，{@code null} 或空数组返回 0
     * @return 压缩后长度 / 原长，范围 {@code [0,1]}
     */
    public static double compressionRatio(byte[] data) {
        return ComplexityRatio.ratio(data);
    }

    /**
     * 计算输入串在所有（或指定）已注册算法上的<b>随机性密度最小值</b>（{@code [0,1]}）。
     * <p>字符串按 UTF-8 编码为字节后参与评估。取最小值即「最保守算法」的判定：
     * 仅当所有参与算法都认为该数据高度随机时结果才高，任何算法认为「不像随机」
     * 都会拉低总分——适合用于综合评估一段文本/数据是否形似随机密钥。</p>
     *
     * @param s          待评估字符串，{@code null} 或空串返回 0
     * @param algorithms 参与聚合的算法名（如 {@code "SHANNON"}、{@code "COMPLEXITY_RATIO"}）；
     *                   为空表示默认核心算法集（SHANNON、COMPLEXITY_RATIO）
     * @return 所有参与算法密度值的最小值；无参与算法时返回 0
     * @throws IllegalArgumentException 若指定的算法名未注册
     */
    public static double minDensity(String s, String... algorithms) {
        if (s == null || s.isEmpty()) {
            return 0.0;
        }
        return minDensity(s.getBytes(StandardCharsets.UTF_8), algorithms);
    }

    /**
     * 计算输入字节在所有（或指定）已注册算法上的<b>随机性密度最小值</b>（{@code [0,1]}），
     * 语义同 {@link #minDensity(String, String...)}。
     *
     * @param data       待评估字节数组，{@code null} 或空数组返回 0
     * @param algorithms 参与聚合的算法名；为空表示默认核心算法集（SHANNON、COMPLEXITY_RATIO）
     * @return 所有参与算法密度值的最小值；无参与算法时返回 0
     * @throws IllegalArgumentException 若指定的算法名未注册
     */
    public static double minDensity(byte[] data, String... algorithms) {
        if (data == null || data.length == 0) {
            return 0.0;
        }
        String[] names = algorithms.length == 0
                ? DEFAULT_AGGREGATION.toArray(new String[0])
                : algorithms;
        double min = Double.POSITIVE_INFINITY;
        for (String name : names) {
            min = Math.min(min, density(name, data));
        }
        return min;
    }

    // ── 查询 ──

    /**
     * 返回当前已注册的所有熵度量算法名（不可变视图）。
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
    public static synchronized Map<Class<? extends EntropyMetric>, Set<String>> registeredMetrics() {
        Map<Class<? extends EntropyMetric>, Set<String>> result = new LinkedHashMap<>();
        for (var entry : REGISTERED_PROTOTYPES.entrySet()) {
            result.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }
}
