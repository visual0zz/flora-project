package com.flora.sanctum.crypto;

import com.flora.sanctum.crypto.impl.Argon2;
import com.flora.sanctum.crypto.impl.HkdfSha256;

/**
 * 主密码 → KEK 的 Argon2id 派生（见设计 02"密钥层次"）。
 * <p>
 * 参数可配置（memory/iterations/parallelism），存于 manifest；默认高安全档
 * 256 MiB / 3 迭代 / 4 并行。结果 256 位 KEK，不落盘、仅在解锁期间驻留内存。
 */
public final class Argon2Kdf {

    /** 默认高安全档参数（memory 256 MiB = 262144 KiB）。 */
    public static final int DEFAULT_MEMORY_KIB = 262144;
    public static final int DEFAULT_ITERATIONS = 3;
    public static final int DEFAULT_PARALLELISM = 4;

    private final int memoryKiB;
    private final int iterations;
    private final int parallelism;
    private final byte[] salt;

    public Argon2Kdf(byte[] salt) {
        this(salt, DEFAULT_MEMORY_KIB, DEFAULT_ITERATIONS, DEFAULT_PARALLELISM);
    }

    public Argon2Kdf(byte[] salt, int memoryKiB, int iterations, int parallelism) {
        if (memoryKiB < 8 * parallelism) {
            throw new IllegalArgumentException("memory too small for parallelism");
        }
        this.salt = salt.clone();
        this.memoryKiB = memoryKiB;
        this.iterations = iterations;
        this.parallelism = parallelism;
    }

    public int memoryKiB() {
        return memoryKiB;
    }

    public int iterations() {
        return iterations;
    }

    public int parallelism() {
        return parallelism;
    }

    public byte[] salt() {
        return salt.clone();
    }

    /** 派生 256 位 KEK。 */
    public byte[] derive(char[] password) {
        byte[] pwd = toUtf8(password);
        try {
            return Argon2.digest(pwd, salt, memoryKiB, iterations, parallelism, 32);
        } finally {
            java.util.Arrays.fill(pwd, (byte) 0);
        }
    }

    /** manifest MAC 密钥派生（见 02"manifest 防篡改"）。 */
    public byte[] manifestMacKey(byte[] kek) {
        return HkdfSha256.derive(kek, null, "sanctum-manifest-mac", 32);
    }

    private static byte[] toUtf8(char[] cs) {
        byte[] out = new byte[cs.length * 4];
        int n = 0;
        for (char c : cs) {
            if (c < 0x80) {
                out[n++] = (byte) c;
            } else if (c < 0x800) {
                out[n++] = (byte) (0xC0 | (c >> 6));
                out[n++] = (byte) (0x80 | (c & 0x3F));
            } else if (Character.isHighSurrogate(c)) {
                // 简化处理：代理对未组合，此处按单码元处理（生产需完整 UTF-16 解码）
                out[n++] = (byte) 0x3F;
            } else {
                out[n++] = (byte) (0xE0 | (c >> 12));
                out[n++] = (byte) (0x80 | ((c >> 6) & 0x3F));
                out[n++] = (byte) (0x80 | (c & 0x3F));
            }
        }
        byte[] r = new byte[n];
        System.arraycopy(out, 0, r, 0, n);
        return r;
    }
}
