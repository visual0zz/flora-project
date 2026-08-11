package com.flora.crypto.schemes;

import com.flora.crypto.newcore.interfaces.algorithm.EntropySource;

/**
 * 方案运行环境。
 * <p>向协议实现注入运行所需的环境。算法级协议（如 {@code keyexchange.KeyExchange}）本质只需熵源；
 * 组合级协议编排（AKE、安全信道等）可在此基础上扩展注入传输、原语解析等。</p>
 */
public interface SchemeContext {

    /** @return 熵源，用于密钥生成等随机运算 */
    EntropySource entropy();
}
