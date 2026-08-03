package com.flora.crypto.schemes;

import com.flora.crypto.core.interfaces.provider.AlgorithmFamily;

/**
 * 密码学方案（scheme）基接口。
 * <p>对应 {@code com.flora.crypto.core} 中的 {@link AlgorithmFamily}：每个方案自述其名称、
 * 支持的方案集合与分发优先级，供 {@link SchemeProvider} 读取并注册。</p>
 * <p>本接口仅承载静态自述信息（无状态），具体的生命周期契约由各个协议族接口
 * （如 {@code keyexchange.KeyExchange}）声明。</p>
 */
public interface Scheme extends AlgorithmFamily {
    // 继承: String getAlgorithmName(); Set<String> supportedAlgorithms(); int priority()
}
