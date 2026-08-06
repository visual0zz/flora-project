package com.flora.crypto.core;

import java.util.Set;
import java.util.function.Function;

/**
 * 算法工厂自述接口。
 * <p>{@link CryptoProvider} 注册时由本工厂自述 DSL 名、优先级、具体度与参数类型，并负责生产算法实例。
 * 注意：本接口<b>不含</b> {@code getAlgorithmName()} —— 那是工厂<em>生产出的算法实现对象</em>才需要的
 * （见 {@code AlgorithmFamily}）。</p>
 * <p>具体度（specificity）语义：值越小越优先，默认等于自述名字数量（与 {@code SchemeProvider} 一致，
 * 即支持算法越多越「通用」、具体度越低）。通用适配器（如 JDK 适配器覆盖多种算法）应覆写
 * {@link #specificity()} 返回其支持集合的大小，以在裁决中保持「通用让位于专用」的行为。</p>
 */
public interface AlgorithmFactory {

    /** @return 自述的 DSL 名集合（支持大小写别名等多名注册） */
    Set<String> names();

    /**
     * 设定本工厂实例当前工作的算法名。
     * <p>类级批量注册（{@code CryptoProvider.register(AlgorithmKind, Class)}）会先以无参构造创建实例，
     * 再于注册每个名字前调用本方法注入算法名；之后 {@link #names()} 返回被注入的名字。</p>
     *
     * @param name 算法 DSL 名
     */
    default void setAlgorithm(String name) {
        throw new UnsupportedOperationException(
                getClass().getSimpleName() + " 不支持算法名注入");
    }

    /** @return 自述优先级，越大越优先 */
    int priority();

    /**
     * 自述具体度，越小越优先。
     *
     * @return 默认等于 {@link #names()} 的数量；通用适配器应覆写以返回支持集合大小
     */
    default int specificity() {
        return names().size();
    }

    /**
     * 自述工厂参数类型。
     *
     * @return 非空数组表示该算法须以 {@code name(args...)} 形式调用；空数组表示无参
     */
    Class<?>[] paramTypes();

    /** @return 按已解析的参数生产算法实例 */
    Object create(Object[] args);

    /** 便捷构造：多名、指定优先级与参数类型、工厂方法。 */
    static AlgorithmFactory of(Set<String> names, int priority, Class<?>[] paramTypes,
                               Function<Object[], ?> factory) {
        return new AlgorithmFactory() {
            @Override
            public Set<String> names() {
                return names;
            }

            @Override
            public int priority() {
                return priority;
            }

            @Override
            public Class<?>[] paramTypes() {
                return paramTypes;
            }

            @Override
            public Object create(Object[] args) {
                return factory.apply(args);
            }
        };
    }

    /** 便捷构造：单一名、默认优先级 0、无参数。 */
    static AlgorithmFactory of(String name, Function<Object[], ?> factory) {
        return of(Set.of(name), 0, new Class<?>[0], factory);
    }

    /** 便捷构造：多名、默认优先级 0、无参数。 */
    static AlgorithmFactory of(Set<String> names, Function<Object[], ?> factory) {
        return of(names, 0, new Class<?>[0], factory);
    }
}
