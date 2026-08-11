package com.flora.crypto.core;

import java.util.Set;

/**
 * 算法工厂自述接口。
 * <p>{@link CryptoProvider} 注册时由本工厂自述 DSL 名、优先级、具体度与参数类型，并负责生产算法实例。
 * 注意：本接口<b>不含</b> {@code getAlgorithmName()} —— 那是工厂<em>生产出的算法实现对象</em>才需要的
 * （见 {@code AlgorithmFactory}）。</p>
 * <p>具体度（specificity）语义：值越小越优先，默认等于自述名字数量（与 {@code SchemeProvider} 一致，
 * 即支持算法越多越「通用」、具体度越低）。通用适配器（如 JDK 适配器覆盖多种算法）应覆写
 * {@link #specificity()} 返回其支持集合的大小，以在裁决中保持「通用让位于专用」的行为。</p>
 */
public interface AlgorithmFactory {

    /**
     * 自述本工厂类支持的算法 DSL 名集合（全集，与实例是否通过 {@link #chooseAlgorithm(String)} 注入无关）。
     *
     * @return 该类可生产的所有算法名
     */
    Set<String> supportedAlgorithms();

    /**
     * 设定本工厂实例当前工作的算法名。
     * <p>类级批量注册（{@code CryptoProvider.register(AlgorithmKind, Class)}）会先以无参构造创建实例，
     * 再于注册每个名字前调用本方法注入算法名。本方法仅决定 {@code create()} 生产哪个算法，
     * 不影响 {@link #supportedAlgorithms()} 返回的全集。</p>
     * <p>默认实现为空操作：单名工厂或对所有名字产出相同算法的工厂无需覆写；
     * 同一实例需按名字区分产出（同名全集、不同实现）的多名工厂才需覆写此方法。</p>
     *
     * @param name 算法 DSL 名
     */
    default void chooseAlgorithm(String name) {
    }

    /** @return 自述优先级，越大越优先 */
    int priority();

    /**
     * 自述具体度，越小越优先。
     *
     * @return 默认等于 {@link #supportedAlgorithms()} 的数量；通用适配器应覆写以返回支持集合大小
     */
    default int specificity() {
        return supportedAlgorithms().size();
    }

    /**
     * 自述工厂参数类型。
     *
     * @return 非空数组表示该算法须以 {@code name(args...)} 形式调用；空数组表示无参
     */
    Class<?>[] paramTypes();

    /** @return 按已解析的参数生产算法实例 */
    Object create(Object[] args);
}
