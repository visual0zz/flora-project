package com.flora.crypto.core.interfaces.provider;

/**
 * 基于 {@link Digest} 的派生函数子接口。
 * <p>如 KDF1 / KDF2（ISO 18033）、哈希式口令派生等，内部以一个 {@link Digest} 为原语。</p>
 */
public interface DigestDerivationFunction extends DerivationFunction {

    /** @return 内部使用的摘要引擎 */
    Digest getDigest();
}
