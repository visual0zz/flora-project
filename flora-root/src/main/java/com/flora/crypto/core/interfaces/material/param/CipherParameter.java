package com.flora.crypto.core.interfaces.material.param;

/**
 * 密码参数根接口。
 * <p>所有算法初始化参数（密钥、IV、随机数等）的顶层抽象，替代散落在各算法里的 {@code init} 入参。
 * 具体形态由子接口表达：{@link KeyParameter}、{@link ParameterWithIV}、{@link ParameterWithRandom} 等。</p>
 */
public interface CipherParameter {
}
