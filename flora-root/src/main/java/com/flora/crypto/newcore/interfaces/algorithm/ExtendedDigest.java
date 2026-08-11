package com.flora.crypto.newcore.interfaces.algorithm;

/**
 * 扩展摘要接口。
 * <p>在 {@link Digest} 基础上暴露内部压缩「块长度」，供 KDF / HMAC / 填充等需要
 * 算法内部块大小的场景使用。JDK 的 {@code MessageDigest} 不直接暴露该值，
 * 由各适配器按算法名给出（未知算法返回 0）。</p>
 */
public interface ExtendedDigest extends Digest {

    /**
     * @return 摘要算法的内部块长度（字节），如 SHA-256 为 64、SHA-512 为 128
     */
    int getByteLength();
}
