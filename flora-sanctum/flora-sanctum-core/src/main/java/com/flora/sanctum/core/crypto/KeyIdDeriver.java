package com.flora.sanctum.core.crypto;

import com.flora.sanctum.core.crypto.impl.Involution;

/**
 * keyId 派生（见设计"keyId 防关联"）。
 * <p>
 * <pre>
 * dekId = SHA-256(keyMaterial)[0:8]                     // 内部标识（索引键）
 * keyId = f(repoKeyIdSeed ‖ nonce, dekId)               // 镜像 Feistel 对合，可逆
 * 恢复：dekId = f(repoKeyIdSeed ‖ nonce, keyId)
 * </pre>
 * nonce 每密文随机（GCM-SIV 参数，密文头明文）→ 同密钥的 keyId 独立随机（防关联）；
 * 对合可逆 → 解密从 (nonce, keyId) 恢复 dekId 定位（O(1)）。内部存储与外部加密数据同一机制。
 */
public final class KeyIdDeriver {

    private KeyIdDeriver() {
    }

    /** 内部标识（索引键，64 位）。 */
    public static byte[] dekId(byte[] keyMaterial) {
        byte[] h = sha256(keyMaterial);
        byte[] out = new byte[Involution.FEISTEL_BLOCK_BYTES];
        System.arraycopy(h, 0, out, 0, out.length);
        return out;
    }

    /** 生成 keyId（加密时写入密文头）。 */
    public static byte[] makeKeyId(byte[] repoKeyIdSeed, byte[] nonce, byte[] keyMaterial) {
        return Involution.apply(concat(repoKeyIdSeed, nonce), dekId(keyMaterial));
    }

    /** 从密文头 (nonce, keyId) 恢复内部标识 dekId（对合可逆）。 */
    public static byte[] resolveDekId(byte[] repoKeyIdSeed, byte[] nonce, byte[] keyId) {
        return Involution.apply(concat(repoKeyIdSeed, nonce), keyId);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static byte[] sha256(byte[] in) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            return md.digest(in);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
