package com.flora.entropy.mesure;

import com.flora.crypto.core.interfaces.provider.AlgorithmFamily;
import com.flora.entropy.mesure.engine.BaseAlphabetEntropy;
import com.flora.entropy.mesure.engine.ComplexityRatio;
import com.flora.entropy.mesure.engine.EnglishMarkovEntropy;
import com.flora.entropy.mesure.engine.ShannonEntropy;
import com.flora.java.CheckUtil;

import java.nio.charset.StandardCharsets;
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
 * 熵度量算法注册表与归一化汇总层（模仿 JCA 的 {@code Provider} / BouncyCastleProvider 模式）。
 * <p>实现类通过 {@link AlgorithmFamily} 自述支持的算法集合与优先级。
 * 注册表按算法名索引，同一算法名可被多个实现类注册，分发时按
 * 「能实现 → 优先级（越大越优先）→ 具体度（算法数越少越优先）」裁决。</p>
 *
 * <p>算法只实现 {@code measure(byte[])} 输出熵总量；本层负责<b>密度归一化</b>：
 * 熵上限按输入字节长度推导（{@link #maxPerByte(int)}），密度 = 熵总量 / 上限；
 * 聚合取所有算法密度的最小值（{@link #minDensity}）即「最保守算法」的判定。</p>
 *
 * <pre>{@code
 * EntropyMetric metric = EntropyProvider.metric("SHANNON");
 * double entropy = metric.measure(data);
 * double density = EntropyProvider.density("SHANNON", data);
 * double minD = EntropyProvider.minDensity(data);
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

    /**
     * 默认聚合的核心算法集合。
     * <p>仅包含有独立贡献的算法：{@code SHANNON}（分布熵）与 {@code COMPLEXITY_RATIO}
     * （压缩不可压缩度，独立视角）。base 编码与英文马尔可夫等算法的度量要么与香农熵
     * 同构（纯编码数据下相等）、要么恒不小于香农熵（交叉熵性质），在取最小值的聚合中
     * 不会成为决定性因子，故不作为默认成员——需要时在 {@code minDensity} 显式指定。</p>
     */
    private static final Set<String> DEFAULT_AGGREGATION = Set.of("SHANNON", "COMPLEXITY_RATIO");

    static {
        registerMetric(new ShannonEntropy(), name -> new ShannonEntropy());
        registerMetric(new ComplexityRatio(), name -> new ComplexityRatio());
        // 常见 base 编码字符熵：BASE16/64/64URL，一个原型声明全部算法名，按名创建实例
        registerMetric(BaseAlphabetEntropy.instance("BASE16"), BaseAlphabetEntropy::instance);
        // 一阶英文马尔可夫（与英文的交叉熵，同为每字节熵，统一参与聚合）
        registerMetric(new EnglishMarkovEntropy(), name -> new EnglishMarkovEntropy());
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
     * @param name 算法名（如 {@code "SHANNON"}、{@code "COMPLEXITY_RATIO"}）
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
