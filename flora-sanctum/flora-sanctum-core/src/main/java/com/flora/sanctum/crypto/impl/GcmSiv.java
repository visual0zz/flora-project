package com.flora.sanctum.crypto.impl;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;

/**
 * AES-GCM-SIV（RFC 8452）集成实现，纯 Java 无第三方依赖。
 * <p>固定 12 字节 nonce，支持 16/24/32 字节密钥（加密子密钥长度随密钥派生自动适配）。
 * 单个类承载密钥派生（counter 块取前 8 字节）、POLYVAL（GF(2^128) 小端字序）、
 * tag 生成（POLYVAL 结果 XOR nonce 前 12 字节 + 清最高位后 AES 加密）与
 * CTR 加密（初始计数器块 = tag 且最高位置 1，仅递增低 4 字节）。
 * 底层 AES 块引擎复用 JDK {@code AES/ECB/NoPadding}（裸块变换，组合逻辑全自研）。
 * GCM-SIV 认证的是明文，解密时先 CTR 恢复明文再验证 tag。</p>
 */
public final class GcmSiv {

    private static final int BLOCK = 16;
    private static final int TAG_LEN = 16;
    private static final int NONCE_LEN = 12;

    private GcmSiv() {
    }

    /**
     * 加密：输出 {@code 密文 ‖ tag}（tag 16 字节）。
     *
     * @param key       AES 密钥（16/24/32 字节）
     * @param nonce     12 字节 nonce
     * @param aad       认证附加数据（可空）
     * @param plaintext 明文
     */
    public static byte[] encrypt(byte[] key, byte[] nonce, byte[] aad, byte[] plaintext) {
        checkParams(key, nonce);
        long[] h = deriveAuthKey(key, nonce);
        byte[] k2 = deriveEncKey(key, nonce);

        long[] s = polyval(h, aad, plaintext);
        byte[] sb = toBytes(s);
        for (int i = 0; i < NONCE_LEN; i++) {
            sb[i] ^= nonce[i];
        }
        sb[BLOCK - 1] &= 0x7f;
        byte[] tag = aesBlock(k2, sb);

        byte[] counter = tag.clone();
        counter[BLOCK - 1] |= 0x80;
        byte[] ct = new byte[plaintext.length];
        ctrXor(k2, counter, plaintext, 0, plaintext.length, ct, 0);
        return concat(ct, tag);
    }

    /**
     * 解密并验证 tag，认证失败抛 {@link IllegalArgumentException}。
     *
     * @param key              AES 密钥（与加密一致）
     * @param nonce            12 字节 nonce
     * @param aad              认证附加数据（与加密一致）
     * @param ciphertextWithTag {@code 密文 ‖ tag}
     */
    public static byte[] decrypt(byte[] key, byte[] nonce, byte[] aad, byte[] ciphertextWithTag) {
        checkParams(key, nonce);
        if (ciphertextWithTag.length < TAG_LEN) {
            throw new IllegalArgumentException("ciphertext too short");
        }
        int ctLen = ciphertextWithTag.length - TAG_LEN;
        byte[] tag = Arrays.copyOfRange(ciphertextWithTag, ctLen, ciphertextWithTag.length);
        byte[] ct = Arrays.copyOf(ciphertextWithTag, ctLen);

        long[] h = deriveAuthKey(key, nonce);
        byte[] k2 = deriveEncKey(key, nonce);

        byte[] counter = tag.clone();
        counter[BLOCK - 1] |= 0x80;
        byte[] pt = new byte[ctLen];
        ctrXor(k2, counter, ct, 0, ctLen, pt, 0);

        long[] s = polyval(h, aad, pt);
        byte[] sb = toBytes(s);
        for (int i = 0; i < NONCE_LEN; i++) {
            sb[i] ^= nonce[i];
        }
        sb[BLOCK - 1] &= 0x7f;
        byte[] expected = aesBlock(k2, sb);
        if (!constantTimeEquals(expected, tag)) {
            throw new IllegalArgumentException("GCM-SIV authentication failed");
        }
        return pt;
    }

    /** 常量时间比较，避免 tag 校验时序侧信道。 */
    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }

    // ===== 密钥派生（RFC 8452 §4）=====

    /** 认证子密钥 H（POLYVAL 密钥）：counter 0/1 两块 AES 输出的前 8 字节。 */
    private static long[] deriveAuthKey(byte[] key, byte[] nonce) {
        byte[] block = new byte[BLOCK];
        System.arraycopy(nonce, 0, block, BLOCK - NONCE_LEN, NONCE_LEN);
        byte[] h = new byte[BLOCK];
        byte[] out = aesBlock(key, block);
        System.arraycopy(out, 0, h, 0, 8);
        block[0]++;
        out = aesBlock(key, block);
        System.arraycopy(out, 0, h, 8, 8);
        return fromBytes(h);
    }

    /** 加密子密钥 K2：counter 2..5 四块 AES 输出的前 8 字节（AES-256）。 */
    private static byte[] deriveEncKey(byte[] key, byte[] nonce) {
        byte[] block = new byte[BLOCK];
        System.arraycopy(nonce, 0, block, BLOCK - NONCE_LEN, NONCE_LEN);
        block[0] = 2;
        byte[] k2 = new byte[key.length];
        for (int i = 0; i < key.length / 8; i++) {
            byte[] out = aesBlock(key, block);
            System.arraycopy(out, 0, k2, i * 8, 8);
            block[0]++;
        }
        return k2;
    }

    // ===== POLYVAL（RFC 8452 §3，小端字序 GF(2^128)）=====

    /**
     * POLYVAL（RFC 8452 §3）：输入 = pad(A) ‖ pad(P) ‖ LE64(bitlen(A)) ‖ LE64(bitlen(P))。
     * 每 16 字节块按小端字序读取，{@code S = dot(S ⊕ X, H)} 迭代，其中
     * {@code dot(a, b) = a·b·x^-128}——把因子预先乘入 H 得 {@code M = H·x^-128}，
     * 再用普通域乘法迭代。
     */
    private static long[] polyval(long[] h, byte[] aad, byte[] data) {
        long[] m = mul128(h, X_INV_128); // H·x^-128
        long[] s = new long[] {0, 0};
        for (int off = 0; off < aad.length; off += BLOCK) {
            long[] x = readBlock(aad, off, Math.min(BLOCK, aad.length - off));
            s = mul128(new long[] {s[0] ^ x[0], s[1] ^ x[1]}, m);
        }
        for (int off = 0; off < data.length; off += BLOCK) {
            long[] x = readBlock(data, off, Math.min(BLOCK, data.length - off));
            s = mul128(new long[] {s[0] ^ x[0], s[1] ^ x[1]}, m);
        }
        long[] lenBlock = {
                aad.length * 8L,   // LE64(bitlen(A))
                data.length * 8L   // LE64(bitlen(P))
        };
        s = mul128(new long[] {s[0] ^ lenBlock[0], s[1] ^ lenBlock[1]}, m);
        return s;
    }

    /** x^-128（POLYVAL 小端位序），满足 x^128·x^-128 = 1。 */
    private static final long[] X_INV_128 = {0x1L, 0x9204000000000000L};

    /** GF(2^128) 乘法（POLYVAL 小端位序），约化多项式 x^128+x^127+x^126+x^121+1。 */
    private static long[] mul128(long[] x, long[] y) {
        long x0 = x[0], x1 = x[1];
        long y0 = y[0], y1 = y[1];
        long z0 = 0, z1 = 0;
        for (int i = 0; i < 64; i++) {
            if ((y0 & 1L) != 0) {
                z0 ^= x0;
                z1 ^= x1;
            }
            y0 >>>= 1;
            boolean carry = (x1 & 0x8000000000000000L) != 0;
            long oldX0 = x0;
            x0 = oldX0 << 1;
            x1 = (x1 << 1) | (oldX0 >>> 63);
            if (carry) {
                x0 ^= 1L;
                x1 ^= 0xC200000000000000L;
            }
        }
        for (int i = 0; i < 64; i++) {
            if ((y1 & 1L) != 0) {
                z0 ^= x0;
                z1 ^= x1;
            }
            y1 >>>= 1;
            boolean carry = (x1 & 0x8000000000000000L) != 0;
            long oldX0 = x0;
            x0 = oldX0 << 1;
            x1 = (x1 << 1) | (oldX0 >>> 63);
            if (carry) {
                x0 ^= 1L;
                x1 ^= 0xC200000000000000L;
            }
        }
        return new long[] {z0, z1};
    }

    // ===== CTR =====

    private static void ctrXor(byte[] k2, byte[] counter, byte[] in, int inOff, int len, byte[] out, int outOff) {
        for (int i = 0; i < len; i += BLOCK) {
            byte[] mask = aesBlock(k2, counter);
            int n = Math.min(BLOCK, len - i);
            for (int j = 0; j < n; j++) {
                out[outOff + i + j] = (byte) (in[inOff + i + j] ^ mask[j]);
            }
            incrementCounter(counter);
        }
    }

    /** 仅递增低 4 字节（小端）。 */
    private static void incrementCounter(byte[] c) {
        for (int i = 0; i < 4; i++) {
            if (++c[i] != 0) {
                break;
            }
        }
    }

    // ===== 底层与工具 =====

    private static final ThreadLocal<Cipher> AES_CIPHER = ThreadLocal.withInitial(() -> {
        try {
            return Cipher.getInstance("AES/ECB/NoPadding");
        } catch (Exception e) {
            throw new IllegalStateException("JDK 缺少 AES 引擎", e);
        }
    });

    private static byte[] aesBlock(byte[] key, byte[] input) {
        try {
            Cipher c = AES_CIPHER.get();
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
            return c.doFinal(input);
        } catch (Exception e) {
            throw new IllegalStateException("AES 块加密失败", e);
        }
    }

    private static long[] readBlock(byte[] in, int off, int len) {
        long v0 = 0;
        long v1 = 0;
        for (int i = 0; i < len && i < 8; i++) {
            v0 |= (in[off + i] & 0xffL) << (8 * i);
        }
        for (int i = 8; i < len && i < 16; i++) {
            v1 |= (in[off + i] & 0xffL) << (8 * (i - 8));
        }
        return new long[] {v0, v1};
    }

    private static long readLE64(byte[] b, int off) {
        return (b[off] & 0xffL) | (b[off + 1] & 0xffL) << 8 | (b[off + 2] & 0xffL) << 16
                | (b[off + 3] & 0xffL) << 24 | (b[off + 4] & 0xffL) << 32 | (b[off + 5] & 0xffL) << 40
                | (b[off + 6] & 0xffL) << 48 | (b[off + 7] & 0xffL) << 56;
    }

    private static byte[] toBytes(long[] v) {
        byte[] b = new byte[BLOCK];
        writeLE64(v[0], b, 0);
        writeLE64(v[1], b, 8);
        return b;
    }

    private static long[] fromBytes(byte[] b) {
        return new long[] {readLE64(b, 0), readLE64(b, 8)};
    }

    private static void writeLE64(long v, byte[] b, int off) {
        b[off] = (byte) v;
        b[off + 1] = (byte) (v >>> 8);
        b[off + 2] = (byte) (v >>> 16);
        b[off + 3] = (byte) (v >>> 24);
        b[off + 4] = (byte) (v >>> 32);
        b[off + 5] = (byte) (v >>> 40);
        b[off + 6] = (byte) (v >>> 48);
        b[off + 7] = (byte) (v >>> 56);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    private static void checkParams(byte[] key, byte[] nonce) {
        if (key == null || (key.length != 16 && key.length != 24 && key.length != 32)) {
            throw new IllegalArgumentException("key 须为 16/24/32 字节");
        }
        if (nonce == null || nonce.length != NONCE_LEN) {
            throw new IllegalArgumentException("nonce 须为 12 字节");
        }
    }
}
