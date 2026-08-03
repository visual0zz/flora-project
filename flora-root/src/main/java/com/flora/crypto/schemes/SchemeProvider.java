package com.flora.crypto.schemes;

import com.flora.crypto.schemes.engine.kex.DhGroup14;
import com.flora.crypto.schemes.keyexchange.KeyExchange;

import com.flora.java.CheckUtil;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * 方案注册与分发器。
 * <p>仿照 {@code com.flora.crypto.core.CryptoProvider} 的「能实现 → 优先级 → 具体度」裁决语义，
 * 但独立实现、不改动 core 的注册机制。协议名与原语名属不同命名空间，分族持有注册表。</p>
 */
public final class SchemeProvider {

    private SchemeProvider() {
    }

    private record Entry<T>(int priority, int specificity, Supplier<? extends T> factory) {
    }

    private static final Map<String, List<Entry<KeyExchange>>> KEX_REGISTRY = new ConcurrentHashMap<>();

    static {
        // 内置密钥交换算法实现（专用，priority 默认 0；多算法/适配器可在此追加并提权）。
        // 调用方无需手动注册，直接经 keyExchange(name) 入口取实例。
        registerKeyExchange(new DhGroup14(), n -> new DhGroup14());
    }

    /**
     * 注册一个密钥交换算法实现（原型实例自述算法名与优先级）。
     *
     * @param prototype 原型实例（经 {@link KeyExchange} 自述支持的方案名）
     * @param factory   按方案名构造实例的工厂
     */
    public static void registerKeyExchange(KeyExchange prototype, Function<String, ? extends KeyExchange> factory) {
        register(KEX_REGISTRY, prototype, factory);
    }

    /**
     * 按方案名解析密钥交换算法。
     *
     * @param name 方案名（如 {@code "diffie-hellman-group14"}）
     * @return 算法实例
     */
    public static KeyExchange keyExchange(String name) {
        return resolve(KEX_REGISTRY, name, "密钥交换算法");
    }

    /** @return 所有已注册的密钥交换方案名 */
    public static Set<String> keyExchangeAlgorithms() {
        return Set.copyOf(KEX_REGISTRY.keySet());
    }

    private static <T> void register(Map<String, List<Entry<T>>> reg, T prototype, Function<String, ? extends T> factory) {
        if (!(prototype instanceof Scheme scheme)) {
            throw new IllegalArgumentException("原型实例必须实现 Scheme: " + prototype.getClass());
        }
        Set<String> algorithms = scheme.supportedAlgorithms();
        if (algorithms == null || algorithms.isEmpty()) {
            throw new IllegalArgumentException("supportedAlgorithms() 不能为空: " + prototype.getClass());
        }
        int priority = scheme.priority();
        int specificity = algorithms.size();
        for (String name : algorithms) {
            reg.computeIfAbsent(name, k -> new CopyOnWriteArrayList<>())
                    .add(new Entry<>(priority, specificity, () -> factory.apply(name)));
        }
    }

    private static <T> T resolve(Map<String, List<Entry<T>>> reg, String name, String role) {
        CheckUtil.notEmpty(name, "方案名不能为空");
        List<Entry<T>> list = reg.get(name);
        if (list == null || list.isEmpty()) {
            throw new IllegalArgumentException("未注册的" + role + ": " + name);
        }
        T result = pick(list);
        if (result == null) {
            throw new IllegalArgumentException("方案重复注册: " + name + " 存在多个同优先级同具体度的提供者");
        }
        return result;
    }

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
