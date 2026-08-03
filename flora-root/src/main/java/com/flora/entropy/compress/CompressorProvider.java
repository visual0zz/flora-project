package com.flora.entropy.compress;

import com.flora.crypto.core.interfaces.provider.AlgorithmFamily;
import com.flora.entropy.compress.engine.DeflateCompressor;
import com.flora.java.CheckUtil;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 压缩组件注册表（模仿 JCA 的 {@code Provider} / BouncyCastleProvider 模式）。
 * <p>实现类通过 {@link AlgorithmFamily} 自述支持的算法集合与优先级。
 * 注册表按算法名索引，同一算法名可被多个实现类注册，分发时按
 * 「能实现 → 优先级（越大越优先）→ 具体度（算法数越少越优先）」裁决。</p>
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

    // ── 注册表：算法名 → 提供者条目列表 ──

    private record Entry<T>(int priority, int specificity, Supplier<? extends T> factory) {
    }

    private static final Map<String, List<Entry<Compressor>>> COMPRESSOR_REGISTRY = new ConcurrentHashMap<>();

    static {
        registerCompressor(DeflateCompressor.of("DEFLATE"), DeflateCompressor::of);
    }

    // ── 注册入口 ──

    /**
     * 注册压缩算法。原型实例须实现 {@link AlgorithmFamily} 以自述支持的算法集合与优先级。
     *
     * @param prototype 原型实例
     * @param factory   按算法名创建新实例的工厂
     */
    public static void registerCompressor(Compressor prototype, Function<String, ? extends Compressor> factory) {
        if (!(prototype instanceof AlgorithmFamily family)) {
            throw new IllegalArgumentException("原型实例必须实现 AlgorithmFamily: " + prototype.getClass());
        }
        Set<String> algorithms = family.supportedAlgorithms();
        if (algorithms == null || algorithms.isEmpty()) {
            throw new IllegalArgumentException("supportedAlgorithms() 不能为空: " + prototype.getClass());
        }
        int priority = family.priority();
        int specificity = algorithms.size();
        for (String name : algorithms) {
            COMPRESSOR_REGISTRY.computeIfAbsent(name, k -> new CopyOnWriteArrayList<>())
                    .add(new Entry<>(priority, specificity, () -> factory.apply(name)));
        }
    }

    // ── 分发入口 ──

    /**
     * 按算法名获取压缩器实例。
     *
     * @param name 算法名（如 {@code "DEFLATE"}）
     * @return 压缩器实例
     * @throws IllegalArgumentException 若算法未注册
     */
    public static Compressor compressor(String name) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        List<Entry<Compressor>> list = COMPRESSOR_REGISTRY.get(name);
        if (list == null || list.isEmpty()) {
            throw new IllegalArgumentException("未注册的压缩算法: " + name);
        }
        Compressor result = pick(list);
        if (result == null) {
            throw new IllegalArgumentException("算法重复注册: " + name + " 存在多个同优先级同具体度的提供者");
        }
        return result;
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
