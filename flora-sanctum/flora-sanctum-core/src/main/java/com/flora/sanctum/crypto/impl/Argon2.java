package com.flora.sanctum.crypto.impl;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

/**
 * Bouncy Castle Argon2 封装。
 */
public final class Argon2 {

    private Argon2() {
    }

    /**
     * Argon2id 摘要。
     *
     * @param password     密码字节
     * @param salt         盐
     * @param memoryKiB    内存 KiB
     * @param iterations   迭代次数
     * @param parallelism  并行度
     * @param outLen       输出长度
     */
    public static byte[] digest(byte[] password, byte[] salt, int memoryKiB, int iterations, int parallelism, int outLen) {
        Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withSalt(salt)
                .withMemoryAsKB(memoryKiB)
                .withIterations(iterations)
                .withParallelism(parallelism)
                .build();
        Argon2BytesGenerator gen = new Argon2BytesGenerator();
        gen.init(params);
        byte[] out = new byte[outLen];
        gen.generateBytes(password, out);
        return out;
    }
}
