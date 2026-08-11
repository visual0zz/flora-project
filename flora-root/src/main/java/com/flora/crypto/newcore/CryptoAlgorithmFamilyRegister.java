package com.flora.crypto.newcore;

import com.flora.common.algorithm.AlgorithmFamily;
import com.flora.common.algorithm.AlgorithmFamilyRegister;
import com.flora.java.CheckUtil;
import com.flora.runtime.log.Logger;
import com.flora.runtime.log.LoggerFactory;
import com.flora.tag.ModuleEntry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 算法工厂注册中心。
 * <p>算法工厂通过 {@link #register(AlgorithmFamily)} 注册；同名冲突按优先级 / 支持集合大小裁决，
 * 查询时按算法名 + 目标角色接口取回对应的算法工厂。</p>
 */
@ModuleEntry
public final class CryptoAlgorithmFamilyRegister implements AlgorithmFamilyRegister {

    private static final Logger LOGGER = LoggerFactory.getLogger(CryptoAlgorithmFamilyRegister.class);

    private CryptoAlgorithmFamilyRegister() {}

    /** 算法名 → 工厂。每个算法名由裁决后胜出的唯一工厂负责。 */
    private final Map<String, AlgorithmFamily<?>> REGISTRY = new ConcurrentHashMap<>();

    /**
     * 注册一个算法工厂。
     * <p>工厂自述 {@link AlgorithmFamily#supportedAlgorithms()} 返回其支持的全部算法名（全集），
     * 每个名字在全局注册表中须唯一。同名冲突时按以下裁决规则逐名处理：</p>
     * <ul>
     *   <li>新工厂优先级更高 → 替换该名字对应的旧工厂；</li>
     *   <li>新工厂优先级更低 → 放弃（保留旧工厂）；</li>
     *   <li>优先级相同时，比较 {@code supportedAlgorithms()} 集合大小：
     *       新的更小（更具体）→ 替换；新的更大 → 不替换；
     *       二者相等 → 视为算法族重复，记录错误日志并抛异常。</li>
     * </ul>
     *
     * @param factory 算法工厂实例
     * @throws IllegalArgumentException 工厂支持集合为空、包含空算法名，或算法族重复无法区分
     */
    public void register(AlgorithmFamily<?> factory) {
        CheckUtil.notNull(factory, "算法工厂不能为空");
        var names = factory.supportedAlgorithms();
        CheckUtil.mustTrue(names != null && !names.isEmpty(),
                factory.getClass().getSimpleName() + " 的 supportedAlgorithms() 返回空集合");
        for (String name : names) {
            CheckUtil.notEmpty(name, "算法名不能为空");
            AlgorithmFamily<?> existing = REGISTRY.get(name);
            if (existing == null) {
                REGISTRY.put(name, factory);
                continue;
            }
            resolveConflict(name, factory, existing);
        }
    }

    /**
     * 裁决单个算法名上旧、新工厂的冲突归属。
     * <p>比较依据是工厂整体（优先级、支持集合大小），裁决结果仅作用于该算法名这一个 key，
     * 不影响两个工厂各自支持的其他名字。优先级与支持集合大小均相同（无法区分）时，
     * 先记录错误日志再抛 {@link IllegalArgumentException} 中断注册。</p>
     */
    private void resolveConflict(String name, AlgorithmFamily<?> candidate, AlgorithmFamily<?> current) {
        int priCmp = Integer.compare(candidate.priority(), current.priority());
        if (priCmp > 0) {
            // 新工厂优先级更高，替换
            REGISTRY.put(name, candidate);
            return;
        }
        if (priCmp < 0) {
            // 新工厂优先级更低，放弃
            return;
        }
        // 优先级相同，比较支持集合大小
        int candidateSize = candidate.supportedAlgorithms().size();
        int currentSize = current.supportedAlgorithms().size();
        if (candidateSize < currentSize) {
            // 新的更具体，替换
            REGISTRY.put(name, candidate);
        } else if (candidateSize > currentSize) {
            // 新的更宽泛，不替换，保持现状
        } else {
            LOGGER.error("算法族重复：算法名 '{}' 已由 {} 注册，优先级({})与支持算法数({})均相同，"
                            + "与 {} 冲突",
                    name, current.getClass().getSimpleName(), current.priority(), currentSize,
                    candidate.getClass().getSimpleName());
            throw new IllegalArgumentException(
                    "算法族重复：算法名 '" + name + "' 已由 " + current.getClass().getSimpleName()
                            + " 注册，优先级与支持算法数均相同，无法与 "
                            + candidate.getClass().getSimpleName() + " 区分");
        }
    }

    /**
     * 按算法名与工厂类型取回对应的算法工厂。
     * <p>注册中心仅负责「注册」与「按名取回」；工厂自身的算法组件解析（{@code componentTypes()} /
     * {@code construct(...)}）不在本类职责范围内，由各工厂自行处理。</p>
     *
     * @param name        算法名（全局唯一）
     * @param factoryType 期望的工厂类型
     * @param <F>         工厂类型
     * @return 注册的工厂实例（已校验并强转为 {@code F}）
     * @throws UnregisteredAlgorithmException 该算法名未注册
     * @throws IllegalArgumentException       注册的工厂不是 {@code factoryType} 所指的类型
     */
    public <F extends AlgorithmFamily<?>> F get(String name, Class<F> factoryType) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        CheckUtil.notNull(factoryType, "工厂类型不能为空");
        AlgorithmFamily<?> factory = REGISTRY.get(name);
        if (factory == null) {
            throw new UnregisteredAlgorithmException(name);
        }
        if (!factoryType.isInstance(factory)) {
            throw new IllegalArgumentException(
                    "算法 '" + name + "' 注册的工厂是 " + factory.getClass().getSimpleName()
                            + "，不是期望的工厂类型 " + factoryType.getSimpleName());
        }
        return factoryType.cast(factory);
    }
}
