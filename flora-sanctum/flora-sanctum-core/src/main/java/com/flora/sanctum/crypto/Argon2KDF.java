package com.flora.sanctum.crypto;

import com.flora.sanctum.crypto.impl.Argon2;
import com.flora.sanctum.crypto.impl.HkdfSha256;

/**
 * 主密码 → KEK 的 Argon2id 派生（见设计 02"密钥层次"）。
 * <p>
 * 参数可配置（memory/iterations/parallelism），存于 manifest；默认高安全档
 * 256 MiB / 3 迭代 / 4 并行。结果 256 位 KEK，不落盘、仅在解锁期间驻留内存。
 */
public final class Argon2KDF {

    /** 默认高安全档参数（memory 256 MiB = 262144 KiB）。 */
    public static final int DEFAULT_MEMORY_KIB = 262144;
    public static final int DEFAULT_ITERATIONS = 3;
    public static final int DEFAULT_PARALLELISM = 4;

    private final int memoryKiB;
    private final int iterations;
    private final int parallelism;
    private final byte[] salt;

    public Argon2KDF(byte[] salt) {
        this(salt, DEFAULT_MEMORY_KIB, DEFAULT_ITERATIONS, DEFAULT_PARALLELISM);
    }

    public Argon2KDF(byte[] salt, int memoryKiB, int iterations, int parallelism) {
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

    /**
     * 原始 Argon2 派生（输入为任意字节，非口令）：供 KDBX 等外部格式密钥派生使用。
     * <p>KDBX4 以 masterSeed‖复合主密钥 为输入、KdfParameters 的盐/参数调用 Argon2。</p>
     *
     * @param type  {@link Argon2#TYPE_D}/{@link Argon2#TYPE_I}/{@link Argon2#TYPE_ID}
     * @param input 输入字节（KDBX 中为 masterSeed‖复合主密钥）
     * @param outLen 输出长度（KDBX 为 32）
     */
    public static byte[] deriveRaw(int type, byte[] input, byte[] salt,
            int memoryKiB, int iterations, int parallelism, int outLen) {
        return Argon2.digest(type, input, salt, memoryKiB, iterations, parallelism, outLen);
    }

    /** manifest MAC 密钥派生（见 02"manifest 防篡改"）。 */
    public byte[] manifestMacKey(byte[] kek) {
        return HkdfSha256.derive(kek, null, "sanctum-manifest-mac", 32);
    }

    private static byte[] toUtf8(char[] cs) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < cs.length; i++) {
            char c = cs[i];
            if (Character.isHighSurrogate(c) && i + 1 < cs.length && Character.isLowSurrogate(cs[i + 1])) {
                int cp = Character.toCodePoint(c, cs[i + 1]);
                i++;
                writeCodePoint(out, cp);
            } else {
                writeCodePoint(out, c);
            }
        }
        return out.toByteArray();
    }

    private static void writeCodePoint(java.io.ByteArrayOutputStream out, int cp) {
        if (cp < 0x80) {
            out.write(cp);
        } else if (cp < 0x800) {
            out.write(0xC0 | (cp >> 6));
            out.write(0x80 | (cp & 0x3F));
        } else if (cp < 0x10000) {
            out.write(0xE0 | (cp >> 12));
            out.write(0x80 | ((cp >> 6) & 0x3F));
            out.write(0x80 | (cp & 0x3F));
        } else {
            out.write(0xF0 | (cp >> 18));
            out.write(0x80 | ((cp >> 12) & 0x3F));
            out.write(0x80 | ((cp >> 6) & 0x3F));
            out.write(0x80 | (cp & 0x3F));
        }
    }
}
