package com.flora.common.algorithm;

import java.util.Set;

public interface AlgorithmFactory<T extends Algorithm<?>>{
    Class<? extends AlgorithmFamilyRegister> registerTo();

    Set<String> supportedAlgorithms();

    /** @return 自述优先级，越大越优先 */
    int priority();

    /**
     * 算法构造时需要注入的组件类型声明（按下标有序）。
     * <p>本设计用于支持灵活组合算法：组合算法的每个依赖都声明为一个组件类型，
     * 与 {@link #construct(String, AlgorithmComponent...)} 的 {@code components} 参数
     * <b>按下标一一对应</b>——即 {@code components[i]} 必须是 {@code componentTypes()[i]} 的实例。</p>
     * <p>组件分两类，均实现 {@link AlgorithmComponent}：
     * 注入的是另一个算法时，直接传 {@link Algorithm} 实例（算法本身即组件）；
     * 注入的是标量取值时，传 {@link AlgorithmConstant}（如输出长度、轮数、预设 IV）。
     * 例如 {@code HMac} 声明 {@code {Digest.class}}，则构造时 {@code components[0]} 须为一个
     * {@link Digest} 实例；{@code Blake2b} 声明 {@code {AlgorithmConstant.class}}，则
     * {@code components[0]} 须为一个承载输出长度的 {@link AlgorithmConstant}。</p>
     *
     * @return 组件类型数组（按下标有序）；空数组表示该算法无需注入组件（无参原语）
     */
    Class<AlgorithmComponent>[] componentTypes();

    /**
     * 将其他算法的实例（或常量组件）注入算法来进行初始化。
     * <p>{@code components} 的元素须与 {@link #componentTypes()} 按下标一一对应、类型匹配，
     * 顺序与数量均由 {@code componentTypes()} 界定；调用方（如 DSL 解析器）负责按该顺序绑定实参。</p>
     *
     * @param algorithmName 当前工作的算法名
     * @param components     注入的组件（算法实例或 {@link AlgorithmConstant}），按下标与 {@link #componentTypes()} 对应
     * @return 构造好的算法实例
     */
    T construct(String algorithmName, AlgorithmComponent ... components);
}
