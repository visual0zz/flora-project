package com.flora.root.crypto;

/**
 * KeePass 系 KDF 的 Argon2 派生封装（对齐 KeePass KDF 语义）。
 * <p>仅暴露原始 Argon2 派生 {@link #deriveRaw}，供 KDBX / 其他外部保险库格式在已知盐与参数下
 * 由复合主密钥派生变换密钥。不涉及 Sanctum 自身的口令/HKDF/manifest 逻辑。</p>
 */
public final class Argon2Kdf {

    private Argon2Kdf() {
    }

    /**
     * 原始 Argon2 派生（输入为任意字节，非口令）：供 KDBX 等外部格式密钥派生使用。
     * <p>KDBX4 以 masterSeed‖复合主密钥 为输入、KdfParameters 的盐/参数调用 Argon2。</p>
     *
     * @param type  {@link Argon2#TYPE_D}/{@link Argon2#TYPE_I}/{@link Argon2#TYPE_ID}
     * @param input 输入字节（KDBX 中为 masterSeed‖复合主密钥）
     * @param salt  盐
     * @param memoryKiB 内存 KiB
     * @param iterations 迭代次数
     * @param parallelism 并行度（lane 数）
     * @param outLen 输出长度（KDBX 为 32）
     */
    public static byte[] deriveRaw(int type, byte[] input, byte[] salt,
            int memoryKiB, int iterations, int parallelism, int outLen) {
        return Argon2.digest(type, input, salt, memoryKiB, iterations, parallelism, outLen);
    }
}
