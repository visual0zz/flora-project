package com.flora.crypto.core;

/**
 * 基于 {@link Mac} 的派生函数子接口（Bouncy Castle 风格）。
 * <p>如 HKDF-Expand（以 HMAC 为原语）、基于 MAC 的口令派生等。</p>
 */
public interface MacDerivationFunction extends DerivationFunction {

    /** @return 内部使用的 MAC 引擎 */
    Mac getMac();
}
