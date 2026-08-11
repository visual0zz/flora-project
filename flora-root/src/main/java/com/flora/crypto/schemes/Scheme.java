package com.flora.crypto.schemes;

import com.flora.common.algorithm.Algorithm;
import com.flora.common.algorithm.AlgorithmFactory;

import java.util.Set;

/**
 * 密码学方案（scheme）基接口。
 * <p>每个方案自述其名称、支持的方案集合与分发优先级，供 {@link SchemeProvider} 读取并注册。
 * 本接口仅承载静态自述信息（无状态），具体的生命周期契约由各个协议族接口
 * （如 {@code keyexchange.KeyExchange}）声明。</p>
 */
public interface Scheme extends Algorithm<AlgorithmFactory<?>> {

    /** @return 支持的方案名集合，默认仅包含本实例的方案名 */
    default Set<String> supportedAlgorithms() {
        return Set.of(getAlgorithmName());
    }

    /** @return 分发优先级，数字越大越优先；通用适配器保持默认 {@code 0} */
    default int priority() {
        return 0;
    }
}
