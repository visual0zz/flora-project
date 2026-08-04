package com.flora.crypto.core.interfaces.provider;
import com.flora.crypto.core.interfaces.DerivationParameters;

/**
 * 密钥派生函数（KDF / 口令哈希）接口。
 * <p>对应常见方案：HKDF、KDF1/2、scrypt、bcrypt、Argon2 等。
 * JCA 没有第一等的 KDF 抽象，仅有 {@code SecretKeyFactory} 的 PBKDF2，
 * 因此本接口对应 JDK 概念缺口：默认提供最简占位实现 {@code PlaceholderDerivationFunction}，
 * 并随附两个不依赖 JDK 的纯 Java 实现（KDF2、HKDF），以 {@code registerDerivationFunction} 接入。</p>
 */
public interface DerivationFunction extends AlgorithmFamily {

    void init(DerivationParameters params);

    void update(byte[] in, int inOff, int len);

    /**
     * 派生密钥材料并写入 {@code out}。
     *
     * @param out    输出缓冲区
     * @param outOff 起始偏移
     * @param len    期望派生长度（字节）
     * @return 实际写入的字节数
     */
    int generateBytes(byte[] out, int outOff, int len);
}
