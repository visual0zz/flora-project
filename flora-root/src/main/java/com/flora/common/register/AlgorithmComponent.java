package com.flora.common.register;

/**
 * 构成一个算法的「组件」通用结构（标记接口）。
 * <p>组合算法在构造时注入的每一项依赖都统一表达为本类型的某个子类型：
 * 注入的是另一个算法时，直接以 {@link Algorithm} 实例承担（算法本身即组件）；
 * 注入的是标量取值时，以 {@link AlgorithmConstant} 承担。
 * CryptoProvider 在构造组合算法时按元素实际类型分流处理。</p>
 */
public interface AlgorithmComponent {
}
