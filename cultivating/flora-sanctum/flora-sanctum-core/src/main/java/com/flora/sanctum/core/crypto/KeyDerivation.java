package com.flora.sanctum.core.crypto;

/**
 * 密钥派生门面（供业务层调用，封装 encKey/子密钥派生；业务类不直接碰 crypto.impl）。
 * <p>
 * encKey = HKDF-SHA256(DEK, "sanctum-enc")，用于 AES-GCM-SIV 加密对象。
 */
public final class KeyDerivation {

    private KeyDerivation() {
    }

    /** 从 DEK 派生 encKey（对象加密密钥）。 */
    public static byte[] encKey(byte[] dek) {
        return com.flora.sanctum.core.crypto.impl.HkdfSha256.derive(dek, null, "sanctum-enc", 32);
    }

    /** HKDF 派生通用入口。 */
    public static byte[] derive(byte[] ikm, byte[] salt, String info, int len) {
        return com.flora.sanctum.core.crypto.impl.HkdfSha256.derive(ikm, salt, info, len);
    }
}
