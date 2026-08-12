package com.flora.root.crypto.core.interfaces.material.param;

/**
 * 密钥派生（KDF / 口令哈希）参数根接口。
 * <p>具体形态由各派生算法表达：{@code HkdfParameters}、{@code Pbkdf2Parameters}、
 * {@code ScryptParameters}、{@code Argon2Parameters} 等。</p>
 */
public interface DerivationParameter extends CipherParameter {
}
