package com.flora.sanctum.crypto.impl;

/**
 * 镜像 Feistel 对合（8 字节块，7 轮；见探索"自反函数 seed 反推困难"构造 G）。
 * <p>
 * 对称轮序列 L,R,L,R,L,R,L：每个单轮是单侧 XOR（自对合），轮序列镜像 ⇒ 整体自逆
 * （E = a₇∘…∘a₁ 满足 E⁻¹ = a₁∘…∘a₇ = E）。轮函数 F = HMAC-SHA256(key, 半块) 截断 4 字节。
 * <p>
 * 用于 keyId 派生：keyId = f(repoKeyIdSeed‖nonce, dekId)，恢复 dekId = f(repoKeyIdSeed‖nonce, keyId)。
 */
public final class Involution {

    /** 块宽度（= keyId/dekId 宽度，64 位）。 */
    public static final int BLOCK_LEN = 8;
    private static final int HALF = BLOCK_LEN / 2;
    private static final int ROUNDS = 7;

    private Involution() {
    }

    /** 对合 f：8 字节块 → 8 字节块，f(f(x)) = x。 */
    public static byte[] apply(byte[] key, byte[] block) {
        if (block.length != BLOCK_LEN) {
            throw new IllegalArgumentException("block must be " + BLOCK_LEN + " bytes");
        }
        byte[] l = new byte[HALF];
        byte[] r = new byte[HALF];
        System.arraycopy(block, 0, l, 0, HALF);
        System.arraycopy(block, HALF, r, 0, HALF);
        for (int i = 0; i < ROUNDS; i++) {
            boolean onL = (i % 2 == 0); // 轮 0,2,4,6 改 L；轮 1,3,5 改 R
            byte[] target = onL ? l : r;
            byte[] input = onL ? r : l;
            byte[] d = f(key, input);
            for (int j = 0; j < HALF; j++) {
                target[j] ^= d[j];
            }
        }
        byte[] out = new byte[BLOCK_LEN];
        System.arraycopy(l, 0, out, 0, HALF);
        System.arraycopy(r, 0, out, HALF, HALF);
        return out;
    }

    /** 轮函数：HMAC-SHA256(key, input) 截断 4 字节。 */
    private static byte[] f(byte[] key, byte[] input) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"));
            byte[] h = mac.doFinal(input);
            byte[] out = new byte[HALF];
            System.arraycopy(h, 0, out, 0, HALF);
            return out;
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
