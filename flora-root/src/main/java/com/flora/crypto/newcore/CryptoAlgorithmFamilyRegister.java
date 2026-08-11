package com.flora.crypto.newcore;

import com.flora.common.algorithm.AlgorithmFamily;
import com.flora.common.algorithm.AlgorithmFamilyRegister;
import com.flora.java.CheckUtil;
import com.flora.tag.ModuleEntry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 算法工厂注册中心。
 * <p>算法工厂通过 {@link #register(AlgorithmFamily)} 注册；约定所有算法全局不同名，
 * 重复注册同名算法视为错误。查询时按算法名 + 目标角色接口（继承 {@link Algorithm} 的接口）
 * 取回对应的算法对象。</p>
 */
@ModuleEntry
public final class CryptoAlgorithmFamilyRegister implements AlgorithmFamilyRegister {

    private CryptoAlgorithmFamilyRegister() {}

    /** 算法名 → 工厂。全局唯一，同名即冲突。 */
    private final Map<String, AlgorithmFamily<?>> REGISTRY = new ConcurrentHashMap<>();

    /**
     * 注册一个算法工厂。
     * <p>工厂自述 {@link AlgorithmFamily#supportedAlgorithms()} 返回其支持的全部算法名（全集），
     * 每个名字在全局注册表中须唯一，重复注册同名算法将抛错。</p>
     *
     * @param factory 算法工厂实例
     * @throws IllegalArgumentException 工厂支持集合为空或包含已注册的同名算法
     */
    public void register(AlgorithmFamily<?> factory) {
        CheckUtil.notNull(factory, "算法工厂不能为空");
        var names = factory.supportedAlgorithms();
        CheckUtil.mustTrue(names != null && !names.isEmpty(),
                factory.getClass().getSimpleName() + " 的 supportedAlgorithms() 返回空集合");
        for (String name : names) {
            CheckUtil.notEmpty(name, "算法名不能为空");
            AlgorithmFamily<?> existing = REGISTRY.putIfAbsent(name, factory);
            if (existing != null) {
                throw new IllegalArgumentException(
                        "算法名 '" + name + "' 已被 " + existing.getClass().getSimpleName()
                                + " 注册，重复注册视为错误");
            }
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
