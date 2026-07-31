package com.flora.crypto.core.interfaces;

/**
 * 加密参数的标记接口（Bouncy Castle 风格）。
 * <p>所有算法参数（对称密钥、非对称密钥、IV、随机数源等）都实现该标记接口，
 * 引擎在 {@code init} 时按需用 {@code instanceof} 取出自己需要的部分，
 * 从而避免为每种组合建立僵硬的参数子类树。</p>
 */
public interface CipherParameters {
}
