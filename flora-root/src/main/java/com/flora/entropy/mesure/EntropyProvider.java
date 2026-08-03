package com.flora.entropy.mesure;

import com.flora.crypto.core.interfaces.provider.AlgorithmFamily;
import com.flora.entropy.mesure.engine.AlnumClasses;
import com.flora.entropy.mesure.engine.CharacterClasses;
import com.flora.entropy.mesure.engine.ComplexityRatio;
import com.flora.entropy.mesure.engine.NormalizedEntropy;
import com.flora.entropy.mesure.engine.ShannonEntropy;
import com.flora.java.CheckUtil;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 熵度量算法注册表（模仿 JCA 的 {@code Provider} / BouncyCastleProvider 模式）。
 * <p>实现类通过 {@link AlgorithmFamily} 自述支持的算法集合与优先级。
 * 注册表按算法名索引，同一算法名可被多个实现类注册，分发时按
 * 「能实现 → 优先级（越大越优先）→ 具体度（算法数越少越优先）」裁决。</p>
 *
 * <pre>{@code
 * EntropyMetric metric = EntropyProvider.metric("SHANNON");
 * double entropy = metric.measure("some-random-string");
 * }</pre>
 *
 * <h2>自定义算法注册</h2>
 * <pre>{@code
 * EntropyProvider.registerMetric(new MyEntropyMetric(), name -> new MyEntropyMetric());
 * EntropyMetric m = EntropyProvider.metric("MY_METRIC");
 * }</pre>
 */
public final class EntropyProvider {

    private EntropyProvider() {
    }

    // ── 注册表：算法名 → 提供者条目列表 ──

    /** 记录每个原型类声明的算法名，用于 {@link #registeredMetrics()} 查询。 */
    private static final Map<Class<? extends EntropyMetric>, Set<String>> REGISTERED_PROTOTYPES = new LinkedHashMap<>();

    private record Entry<T>(int priority, int specificity, Supplier<? extends T> factory) {
    }

    private static final Map<String, List<Entry<EntropyMetric>>> METRIC_REGISTRY = new ConcurrentHashMap<>();

    static {
        registerMetric(new ShannonEntropy(), name -> new ShannonEntropy());
        registerMetric(new NormalizedEntropy(), name -> new NormalizedEntropy());
        registerMetric(new CharacterClasses(), name -> new CharacterClasses());
        registerMetric(new AlnumClasses(), name -> new AlnumClasses());
        registerMetric(new ComplexityRatio(), name -> new ComplexityRatio());
    }

    // ── 注册入口 ──

    /**
     * 注册熵度量算法。原型实例须实现 {@link AlgorithmFamily} 以自述支持的算法集合与优先级。
     *
     * @param prototype 原型实例
     * @param factory   按算法名创建新实例的工厂
     */
    public static void registerMetric(EntropyMetric prototype, Function<String, ? extends EntropyMetric> factory) {
        if (!(prototype instanceof AlgorithmFamily family)) {
            throw new IllegalArgumentException("原型实例必须实现 AlgorithmFamily: " + prototype.getClass());
        }
        Set<String> algorithms = family.supportedAlgorithms();
        if (algorithms == null || algorithms.isEmpty()) {
            throw new IllegalArgumentException("supportedAlgorithms() 不能为空: " + prototype.getClass());
        }
        int priority = family.priority();
        int specificity = algorithms.size();
        @SuppressWarnings("unchecked")
        Class<? extends EntropyMetric> clazz = (Class<? extends EntropyMetric>) prototype.getClass();
        REGISTERED_PROTOTYPES.computeIfAbsent(clazz, k -> new LinkedHashSet<>()).addAll(algorithms);
        for (String name : algorithms) {
            METRIC_REGISTRY.computeIfAbsent(name, k -> new CopyOnWriteArrayList<>())
                    .add(new Entry<>(priority, specificity, () -> factory.apply(name)));
        }
    }

    // ── 分发入口 ──

    /**
     * 按算法名获取度量实例。
     *
     * @param name 算法名（如 {@code "SHANNON"}、{@code "NORMALIZED"}）
     * @return 度量实例
     * @throws IllegalArgumentException 若算法未注册
     */
    public static EntropyMetric metric(String name) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        List<Entry<EntropyMetric>> list = METRIC_REGISTRY.get(name);
        if (list == null || list.isEmpty()) {
            throw new IllegalArgumentException("未注册的熵度量算法: " + name);
        }
        EntropyMetric result = pick(list);
        if (result == null) {
            throw new IllegalArgumentException("算法重复注册: " + name + " 存在多个同优先级同具体度的提供者");
        }
        return result;
    }

    // ── 查询 ──

    /**
     * 返回当前已注册的所有熵度量算法名（不可变视图）。
     *
     * @return 算法名集合
     */
    public static Set<String> registeredAlgorithms() {
        return Collections.unmodifiableSet(METRIC_REGISTRY.keySet());
    }

    /**
     * 按注册原型类列出每个实现类所支持的算法名。
     * <p>返回的 Map 键为注册时传入的原型实例的 {@code Class}，
     * 值为该实现声明的算法名集合（不可变视图）。</p>
     *
     * @return 实现类 → 算法名的不可变映射
     */
    public static Map<Class<? extends EntropyMetric>, Set<String>> registeredMetrics() {
        Map<Class<? extends EntropyMetric>, Set<String>> result = new LinkedHashMap<>();
        for (var entry : REGISTERED_PROTOTYPES.entrySet()) {
            result.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    /** 按「优先级最大 → 具体度最小」裁决，多个并列返回 null。 */
    private static <T> T pick(List<Entry<T>> list) {
        int maxPri = list.stream().mapToInt(Entry::priority).max().orElse(0);
        var byPri = list.stream().filter(e -> e.priority() == maxPri).toList();
        if (byPri.size() == 1) {
            return byPri.get(0).factory().get();
        }
        int minSpec = byPri.stream().mapToInt(Entry::specificity).min().orElse(0);
        var bySpec = byPri.stream().filter(e -> e.specificity() == minSpec).toList();
        if (bySpec.size() == 1) {
            return bySpec.get(0).factory().get();
        }
        return null;
    }
}
