package com.flora.common.algorithm;

import com.flora.java.CheckUtil;
import com.flora.runtime.log.Logger;
import com.flora.runtime.log.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 算法工厂注册中心的通用抽象实现。
 * <p>承载与具体算法领域无关的注册 / 同名裁决 / 按名查询逻辑，供各领域（加密、解析、存储等）
 * 复用。子类继承本类并自定义实例化方式即可获得一套完整的异构算法注册能力。</p>
 * <p>注册规则：工厂通过 {@link AlgorithmFamily#supportedAlgorithms()} 自述其支持的全部算法名（全集），
 * 每个名字在注册表中唯一。同名冲突时按以下规则逐名裁决：</p>
 * <ul>
 *   <li>新工厂优先级更高 → 替换该名字对应的旧工厂；</li>
 *   <li>新工厂优先级更低 → 放弃（保留旧工厂）；</li>
 *   <li>优先级相同时，比较 {@code supportedAlgorithms()} 集合大小：
 *       新的更小（更具体）→ 替换；新的更大 → 不替换；
 *       二者相等 → 视为算法族重复，记录错误日志并抛异常。</li>
 * </ul>
 *
 * <p>本类使用可变的 {@link ConcurrentHashMap} 作为注册表；如需不可变或其它并发策略，
 * 子类可覆写 {@link #newRegistry()} 提供。</p>
 */
public abstract class AbstractAlgorithmFamilyRegister implements AlgorithmFamilyRegister {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractAlgorithmFamilyRegister.class);

    /** 算法名 → 工厂。每个算法名由裁决后胜出的唯一工厂负责。 */
    private final Map<String, AlgorithmFamily<?>> registry = newRegistry();

    /**
     * @return 注册表容器，默认 {@link ConcurrentHashMap}；子类可覆写
     */
    protected Map<String, AlgorithmFamily<?>> newRegistry() {
        return new ConcurrentHashMap<>();
    }

    @Override
    public void register(AlgorithmFamily<?> factory) {
        CheckUtil.notNull(factory, "算法工厂不能为空");
        var names = factory.supportedAlgorithms();
        CheckUtil.mustTrue(names != null && !names.isEmpty(),
                factory.getClass().getSimpleName() + " 的 supportedAlgorithms() 返回空集合");
        for (String name : names) {
            CheckUtil.notEmpty(name, "算法名不能为空");
            AlgorithmFamily<?> existing = registry.get(name);
            if (existing == null) {
                registry.put(name, factory);
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
    protected void resolveConflict(String name, AlgorithmFamily<?> candidate, AlgorithmFamily<?> current) {
        int priCmp = Integer.compare(candidate.priority(), current.priority());
        if (priCmp > 0) {
            // 新工厂优先级更高，替换
            registry.put(name, candidate);
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
            registry.put(name, candidate);
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

    @Override
    public <F extends AlgorithmFamily<?>> F get(String name, Class<F> factoryType) {
        CheckUtil.notEmpty(name, "算法名不能为空");
        CheckUtil.notNull(factoryType, "工厂类型不能为空");
        AlgorithmFamily<?> factory = registry.get(name);
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
