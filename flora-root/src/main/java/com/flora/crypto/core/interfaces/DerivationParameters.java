package com.flora.crypto.core.interfaces;

/**
 * 派生函数（KDF / 口令哈希）的参数标记接口。
 * <p>具体派生算法各自定义子类型（如 {@code KdfParameters} 持有共享秘密与可选 info，
 * {@code HkdfParameters} 持有伪随机密钥与 info），由 {@link DerivationFunction} 在
 * {@code init} 时按 {@code instanceof} 取出。</p>
 */
public interface DerivationParameters {
}
